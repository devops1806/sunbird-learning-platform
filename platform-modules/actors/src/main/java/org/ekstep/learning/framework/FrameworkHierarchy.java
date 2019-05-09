/**
 * 
 */
package org.ekstep.learning.framework;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.ekstep.common.Platform;
import org.ekstep.common.dto.Request;
import org.ekstep.common.dto.Response;
import org.ekstep.common.exception.ClientException;
import org.ekstep.common.exception.ResourceNotFoundException;
import org.ekstep.common.exception.ResponseCode;
import org.ekstep.common.mgr.BaseManager;
import org.ekstep.common.mgr.ConvertGraphNode;
import org.ekstep.common.optimizr.FileUtils;
import org.ekstep.graph.cache.util.RedisStoreUtil;
import org.ekstep.graph.dac.enums.GraphDACParams;
import org.ekstep.graph.dac.model.Node;
import org.ekstep.graph.dac.model.Relation;
import org.ekstep.graph.engine.router.GraphEngineManagers;
import org.ekstep.graph.model.cache.CategoryCache;
import org.ekstep.graph.model.node.DefinitionDTO;
import org.ekstep.learning.hierarchy.store.HierarchyStore;
import org.ekstep.learning.util.CloudStore;
import org.ekstep.learning.util.FrameworkCache;
import org.ekstep.telemetry.logger.TelemetryManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pradyumna
 *
 */
public class FrameworkHierarchy extends BaseManager {

	private ObjectMapper mapper = new ObjectMapper();

	protected static final String GRAPH_ID = (Platform.config.hasPath("graphId")) ? Platform.config.getString("graphId")
			: "domain";

	private static final String keyspace = Platform.config.hasPath("hierarchy.keyspace.name")
			? Platform.config.getString("hierarchy.keyspace.name")
			: "hierarchy_store";
	private static final String table = Platform.config.hasPath("framework.hierarchy.table")
			? Platform.config.getString("framework.hierarchy.table")
			: "framework_hierarchy";
	private static final String objectType = "Framework";
	private HierarchyStore hierarchyStore = new HierarchyStore(keyspace, table, objectType, false);

	private int frameworkTtl = (Platform.config.hasPath("framework.cache.ttl"))
			? Platform.config.getInt("framework.cache.ttl")
			: 604800;

	private static final String FR_CLOUD_UPLOAD_DIR = "framework";

	/**
	 * @param id
	 * @throws Exception
	 */
	public void generateFrameworkHierarchy(String id) throws Exception {
		Response responseNode = getDataNode(GRAPH_ID, id);
		if (checkError(responseNode))
			throw new ResourceNotFoundException("ERR_DATA_NOT_FOUND", "Data not found with id : " + id);
		Node node = (Node) responseNode.get(GraphDACParams.node.name());
		if (StringUtils.equalsIgnoreCase(node.getObjectType(), "Framework")) {
			Map<String, Object> frameworkDocument = new HashMap<>();
			Map<String, Object> frameworkHierarchy = getHierarchy(node.getIdentifier(), 0, false, true);
			CategoryCache.setFramework(node.getIdentifier(), frameworkHierarchy);

			frameworkDocument.putAll(frameworkHierarchy);
			frameworkDocument.put("identifier", node.getIdentifier());
			frameworkDocument.put("objectType", node.getObjectType());
			DefinitionDTO definition = getDefinition(GRAPH_ID, node.getObjectType());
			String[] fields = getFields(definition);
			for (String field : fields) {
				if(null!=node.getMetadata().get(field))
					frameworkDocument.put(field, node.getMetadata().get(field));
			}
			hierarchyStore.saveOrUpdateHierarchy(node.getIdentifier(),frameworkDocument);
			//saving the framework in the redis sever for publish.
			FrameworkCache.save(node.getIdentifier(), frameworkDocument);
//			RedisStoreUtil.saveData(node.getIdentifier(), frameworkDocument, frameworkTtl);
//			Response response = OK();
//			response.put("framework", frameworkDocument);
//			uploadToCloud(response, id);

		} else {
			throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "The object with given identifier is not a framework: " + id);
		}
	}



	/**
	 *
	 * @param frameworkId
	 * @return frameworkHierarchy
	 * @throws Exception
	 */
	public Map<String, Object> getFrameworkHierarchy(String frameworkId) throws Exception{
		Map<String, Object> hierarchy = hierarchyStore.getHierarchy(frameworkId);
		if(MapUtils.isEmpty(hierarchy)) {
			throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Framework not found : " +
					frameworkId);
		}
		return  hierarchy;
	}

	private void pushFrameworkEvent(Node node) throws Exception {

	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getHierarchy(String id, int index, boolean includeMetadata, boolean includeRelations) throws Exception {
		Map<String, Object> data = new HashMap<String, Object>();
		Response responseNode = getDataNode(GRAPH_ID, id);
		if (checkError(responseNode))
			throw new ResourceNotFoundException("ERR_DATA_NOT_FOUND", "Data not found with id : " + id,
					ResponseCode.RESOURCE_NOT_FOUND);
		Node node = (Node) responseNode.get(GraphDACParams.node.name());

		Map<String, Object> metadata = node.getMetadata();
		String status = (String) metadata.get("status");
		if (StringUtils.equalsIgnoreCase("Live", status)) {
			String objectType = node.getObjectType();
			DefinitionDTO definition = getDefinition(GRAPH_ID, objectType);
			if (includeMetadata) {
				String[] fields = getFields(definition);
				if (fields != null) {
					for (String field : fields) {
						data.put(field, metadata.get(field));
					}
				} else {
					data.putAll(node.getMetadata());
				}
				data.put("identifier", node.getIdentifier());
				if (index > 0)
					data.put("index", index);
			}
			if (includeRelations) {
				Map<String, String> inRelDefMap = new HashMap<>();
				Map<String, String> outRelDefMap = new HashMap<>();
				List<String> sortKeys = new ArrayList<String>();
				ConvertGraphNode.getRelationDefinitionMaps(definition, inRelDefMap, outRelDefMap);
				List<Relation> outRelations = node.getOutRelations();
				if (null != outRelations && !outRelations.isEmpty()) {
					for (Relation relation : outRelations) {
						String type = relation.getRelationType();
						String key = type + relation.getEndNodeObjectType();
						String title = outRelDefMap.get(key);
						List<Map<String, Object>> relData = (List<Map<String, Object>>) data.get(title);
						if (relData == null) {
							relData = new ArrayList<Map<String, Object>>();
							data.put(title, relData);
							if ("hasSequenceMember".equalsIgnoreCase(type))
								sortKeys.add(title);
						}
						Map<String, Object> relMeta = relation.getMetadata();
						int seqIndex = 0;
						if (relMeta != null) {
							Object indexObj = relMeta.get("IL_SEQUENCE_INDEX");
							if (indexObj != null)
								seqIndex = ((Long) indexObj).intValue();
						}
						boolean getChildren = true;
						// TODO: This condition value should get from definition node.
						if ("associations".equalsIgnoreCase(title)) {
							getChildren = false;
						}
						Map<String, Object> childData = getHierarchy(relation.getEndNodeId(), seqIndex, true, getChildren);
						if (!childData.isEmpty())
							relData.add(childData);
					}
				}
				for (String key : sortKeys) {
					List<Map<String, Object>> prop = (List<Map<String, Object>>) data.get(key);
					getSorted(prop);
				}
			}
		}

		return data;
	}

	private DefinitionDTO getDefinition(String graphId, String objectType) {
		Request request = getRequest(graphId, GraphEngineManagers.SEARCH_MANAGER, "getNodeDefinition",
				GraphDACParams.object_type.name(), objectType);
		Response response = getResponse(request);
		if (!checkError(response)) {
			DefinitionDTO definition = (DefinitionDTO) response.get(GraphDACParams.definition_node.name());
			return definition;
		}
		return null;
	}

	private String[] getFields(DefinitionDTO definition) {
		Map<String, Object> meta = definition.getMetadata();
		return (String[]) meta.get("fields");
	}

	private void getSorted(List<Map<String, Object>> relObjects) {
		Collections.sort(relObjects, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> o1, Map<String, Object> o2) {
				int o1Index = (int) o1.get("index");
				int o2Index = (int) o2.get("index");
				return o1Index - o2Index;
			}
		});
	}

	private void uploadToCloud(Response response, String id) {
		File file = new File("/tmp/" + id + ".json");
		try {
			mapper.writeValue(file, response);
			String[] url = CloudStore.uploadFile(FR_CLOUD_UPLOAD_DIR, file, false);
			TelemetryManager.info("Uploaded frameworkHierarchy for " + id + " to cloud store : " + url[1]);
		} catch (Exception e) {
			TelemetryManager.error("Error while uploading framework to cloud store for id: " + id, e);
		} finally {
			FileUtils.deleteFile(file);
		}

	}
}
