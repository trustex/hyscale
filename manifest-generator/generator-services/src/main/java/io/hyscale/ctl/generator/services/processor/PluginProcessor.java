package io.hyscale.ctl.generator.services.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.hyscale.ctl.commons.exception.HyscaleException;
import io.hyscale.ctl.commons.logger.WorkflowLogger;
import io.hyscale.ctl.commons.models.Manifest;
import io.hyscale.ctl.commons.models.ManifestContext;
import io.hyscale.ctl.commons.models.Status;
import io.hyscale.ctl.commons.models.YAMLManifest;
import io.hyscale.ctl.commons.utils.HyscaleFilesUtil;
import io.hyscale.ctl.generator.services.config.ManifestConfig;
import io.hyscale.ctl.generator.services.model.ManifestGeneratorActivity;
import io.hyscale.ctl.generator.services.model.ManifestNode;
import io.hyscale.ctl.generator.services.utils.ManifestTreeUtils;
import io.hyscale.ctl.generator.services.utils.PluginHandlers;
import io.hyscale.ctl.generator.services.generator.ManifestFileGenerator;
import io.hyscale.ctl.models.ManifestMeta;
import io.hyscale.ctl.plugin.ManifestHandler;
import io.hyscale.ctl.plugin.ManifestSnippet;
import io.hyscale.ctl.servicespec.commons.fields.HyscaleSpecFields;
import io.hyscale.ctl.servicespec.commons.model.service.ServiceSpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PluginProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PluginProcessor.class);

    @Autowired
    private ManifestTreeUtils manifestTreeUtils;

    @Autowired
    private ManifestFileGenerator manifestFileGenerator;

    @Autowired
    private PluginHandlers pluginHandlers;

    @Autowired
    private ManifestConfig manifestConfig;

    @Autowired
    private HyscaleFilesUtil filesUtil;

    public List<Manifest> getManifests(ServiceSpec serviceSpec, ManifestContext manifestContext)
            throws HyscaleException {
        YAMLMapper yamlMapper = new YAMLMapper();
        String serviceName = serviceSpec.get(HyscaleSpecFields.name, String.class);
        List<Manifest> manifestList = new ArrayList<>();
        Map<ManifestMeta, ManifestNode> manifestMetavsNodeMap = process(serviceSpec, manifestContext);
        if (manifestMetavsNodeMap == null || manifestMetavsNodeMap.isEmpty()) {
            logger.debug("Found empty processed manifests ");
            return manifestList;
        }
        String manifestDir = manifestConfig.getManifestDir(manifestContext.getAppName(), serviceName);

        manifestMetavsNodeMap.entrySet().stream().forEach(each -> {
            ManifestNode manifestNode = each.getValue();
            try {
                String yamString = null;
                if (manifestNode != null && manifestNode.getObjectNode() != null) {
                    yamString = yamlMapper.writeValueAsString(manifestNode.getObjectNode());
                }
                WorkflowLogger.startActivity(ManifestGeneratorActivity.GENERATING_MANIFEST, each.getKey().getKind());
                YAMLManifest yamlManifest = manifestFileGenerator.getYamlManifest(manifestDir, yamString,
                        each.getKey());
                manifestList.add(yamlManifest);
                WorkflowLogger.endActivity(Status.DONE);
            } catch (HyscaleException e) {
                logger.error("Failed to process manifest {}", each.getKey(), e);
                WorkflowLogger.endActivity(Status.FAILED);
            } catch (JsonProcessingException e) {
                logger.error("Failed to process manifest during yaml conversion {}", each.getKey(), e);
                WorkflowLogger.endActivity(Status.FAILED);
            }
        });
        return manifestList;
    }

    public Map<ManifestMeta, ManifestNode> process(ServiceSpec serviceSpec, ManifestContext manifestContext) {
        List<ManifestHandler> manifestHandlerList = pluginHandlers.getAllPlugins();
        if (manifestHandlerList == null || manifestHandlerList.isEmpty()) {
            return null;
        }
        Map<ManifestMeta, ManifestNode> manifestMetavsNodeMap = new HashMap<>();
        manifestHandlerList.stream().filter(each -> {
            return each != null;
        }).forEach(each -> {
            List<ManifestSnippet> manifestSnippetList = null;
            try {
                logger.debug("Executing plugin handler of : {}", each.getClass().getCanonicalName());
                manifestSnippetList = each.handle(serviceSpec, manifestContext);
                if (validateSnippets(manifestSnippetList)) {
                    logger.debug("Updating plugins snippets of {} plugin handler ", each.getClass().getCanonicalName());
                    updateManifests(manifestSnippetList, manifestMetavsNodeMap);
                }
                logger.debug("Completed execution of {} plugin handler ", each.getClass().getCanonicalName());
            } catch (HyscaleException e) {
                logger.error("Error while executing manifest plugin {} ", each.getClass().getName(), e);
            }
        });
        return manifestMetavsNodeMap;
    }

    private void updateManifests(List<ManifestSnippet> manifestSnippetList,
                                 Map<ManifestMeta, ManifestNode> manifestMetavsNodeMap) {
        if (manifestSnippetList == null || manifestSnippetList.isEmpty()) {
            return;
        }

        List<String> failedSnippets = new ArrayList<>();
        manifestSnippetList.stream().filter(each -> {
            return each != null && StringUtils.isNotBlank(each.getSnippet());
        }).forEach(each -> {
            logger.debug("Processing Snippet Kind{} :: Path{} ", each.getKind(), each.getPath());
            ManifestMeta manifestMeta = new ManifestMeta(each.getKind());
            if (each.getName() != null) {
                manifestMeta.setIdentifier(each.getName());
            }
            ManifestNode manifestNode = manifestMetavsNodeMap.get(manifestMeta);
            ObjectNode rootNode = null;
            if (manifestNode == null || manifestNode.getObjectNode() == null) {
                rootNode = JsonNodeFactory.instance.objectNode();
                manifestNode = new ManifestNode(rootNode);

            } else {
                rootNode = manifestNode.getObjectNode();
            }
            try {
                rootNode = (ObjectNode) manifestTreeUtils.injectSnippet(each.getSnippet(), each.getPath(), rootNode);
                // updating the root node back in the manifests
                manifestNode.setObjectNode(rootNode);
                manifestMetavsNodeMap.put(manifestMeta, manifestNode);

            } catch (IOException e) {
                failedSnippets.add(each.getPath());
                logger.error("Error while Injecting manifest snippet for {} ", each);
            } catch (HyscaleException e) {
                failedSnippets.add(each + ": " + e.getMessage());
                logger.error("Error while Injecting manifest snippet for {} ", each);
            }
        });
        if (!failedSnippets.isEmpty()) {
            WorkflowLogger.warn(ManifestGeneratorActivity.ERROR_WHILE_PROCESSING_MANIFEST_PLUGINS,
                    failedSnippets.toString());
        }
    }

    private boolean validateSnippets(List<ManifestSnippet> manifestSnippetList) {
        if (manifestSnippetList != null && !manifestSnippetList.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

}