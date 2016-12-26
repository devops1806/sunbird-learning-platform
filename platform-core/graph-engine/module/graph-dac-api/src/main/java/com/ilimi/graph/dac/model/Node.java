package com.ilimi.graph.dac.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.neo4j.driver.v1.Record;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;

import com.ilimi.common.exception.ServerException;
import com.ilimi.graph.dac.enums.RelationTypes;
import com.ilimi.graph.dac.enums.SystemNodeTypes;
import com.ilimi.graph.dac.enums.SystemProperties;
import com.ilimi.graph.dac.exception.GraphDACErrorCodes;

public class Node implements Serializable {

	private static final long serialVersionUID = 252337826576516976L;

	// TODO: Make 'DEFAULT_CYPHER_NODE_OBJECT',
	// 'DEFAULT_CYPHER_START_NODE_OBJECT', 'DEFAULT_CYPHER_END_NODE_OBJECT' and
	// Cypher Query Constants should be tied with Single Contract. Right now it
	// is in 'BaseQueryGenerationUtil', 'Neo4JBoltNodeOperation' and 'NODE' DTO,
	// It should be in 'DACConfigurationConstants' or
	// 'CypherConfigurationConstants'.
	private final static String DEFAULT_CYPHER_NODE_OBJECT = "ee";
	private final static String DEFAULT_CYPHER_START_NODE_OBJECT = "__startNode";
	private final static String DEFAULT_CYPHER_END_NODE_OBJECT = "__startNode";
	protected final static String DEFAULT_CYPHER_RELATION_OBJECT = "rel";

	private long id;
	private String graphId;
	private String identifier;
	private String nodeType;
	private String objectType;
	private Map<String, Object> metadata;
	private List<Relation> outRelations;
	private List<Relation> inRelations;
	private List<String> tags;

	public Node() {

	}

	public Node(String identifier, String nodeType, String objectType) {
		this.identifier = identifier;
		this.nodeType = nodeType;
		this.objectType = objectType;
	}

	public Node(String graphId, Map<String, Object> metadata) {
		this.graphId = graphId;
		this.metadata = metadata;
		if (null != metadata && !metadata.isEmpty()) {
			if (null != metadata.get(SystemProperties.IL_UNIQUE_ID.name()))
				this.identifier = metadata.get(SystemProperties.IL_UNIQUE_ID.name()).toString();
			if (null != metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name()))
				this.nodeType = metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name()).toString();
			if (null != metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
				this.objectType = metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).toString();
		}
	}

	public Node(String graphId, Record record) {
    	if (null == record)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Record from database is null.");
    	
    	if (null == record.get(DEFAULT_CYPHER_NODE_OBJECT))
    		throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Node from database is null.");
    	
    	org.neo4j.driver.v1.types.Node node = record.get(DEFAULT_CYPHER_NODE_OBJECT).asNode();
    	this.graphId = graphId;
    	this.id = node.id();
    	Iterable<String> keys = node.keys();
    	if (null != keys && null != keys.iterator()) { 
    		this.metadata = new HashMap<String, Object>();
    		for (String key : keys) {
				if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name()))
					this.identifier = node.get(key).asString();
				else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name()))
					this.nodeType = node.get(key).asString();
				else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
					this.objectType = node.get(key).asString();
				else
					this.metadata.put(key, node.get(key));
			}
    	}
    }

	public Node(String graphId, org.neo4j.graphdb.Node neo4jNode) {
		if (null == neo4jNode)
			throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
					"Failed to create node object. Node from database is null.");
		this.graphId = graphId;
		this.id = neo4jNode.getId();
		Iterable<String> keys = neo4jNode.getPropertyKeys();
		if (null != keys && null != keys.iterator()) {
			this.metadata = new HashMap<String, Object>();
			for (String key : keys) {
				if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name()))
					this.identifier = neo4jNode.getProperty(key).toString();
				else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name()))
					this.nodeType = neo4jNode.getProperty(key).toString();
				else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
					this.objectType = neo4jNode.getProperty(key).toString();
				else
					this.metadata.put(key, neo4jNode.getProperty(key));
			}
		}
		Iterable<Relationship> outRels = neo4jNode.getRelationships(Direction.OUTGOING);
		if (null != outRels && null != outRels.iterator()) {
			this.outRelations = new ArrayList<Relation>();
			for (Relationship outRel : outRels)
				this.outRelations.add(new Relation(graphId, outRel));
		}
		Iterable<Relationship> inRels = neo4jNode.getRelationships(Direction.INCOMING);
		if (null != inRels && null != inRels.iterator()) {
			this.tags = new ArrayList<String>();
			this.inRelations = new ArrayList<Relation>();
			for (Relationship inRel : inRels) {
				Relation rel = new Relation(graphId, inRel);
				if (!isTagRelation(rel))
					this.inRelations.add(rel);
				else {
					this.tags.add(rel.getStartNodeName());
				}
			}
		}
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public String getIdentifier() {
		if (StringUtils.isBlank(identifier) && null != metadata)
			this.identifier = (String) metadata.get(SystemProperties.IL_UNIQUE_ID.name());
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@JsonIgnore
	public String getGraphId() {
		return graphId;
	}

	public void setGraphId(String graphId) {
		this.graphId = graphId;
	}

	@JsonIgnore
	public String getNodeType() {
		if (StringUtils.isBlank(nodeType) && null != metadata)
			this.nodeType = (String) metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name());
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getObjectType() {
		if (StringUtils.isBlank(objectType) && null != metadata)
			this.objectType = (String) metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name());
		return objectType;
	}

	public void setObjectType(String objectType) {
		this.objectType = objectType;
	}

	public List<Relation> getOutRelations() {
		return outRelations;
	}

	public void setOutRelations(List<Relation> outRelations) {
		this.outRelations = outRelations;
	}

	public List<Relation> getInRelations() {
		return inRelations;
	}

	public void setInRelations(List<Relation> inRelations) {
		this.inRelations = inRelations;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	private boolean isTagRelation(Relation rel) {
		if (StringUtils.equals(SystemNodeTypes.TAG.name(), rel.getStartNodeType())
				&& StringUtils.equals(RelationTypes.SET_MEMBERSHIP.relationName(), rel.getRelationType()))
			return true;
		return false;
	}
	
	private List<Relation> getOutgoingRelationships(Record record) {
		List<Relation> outRelationships = new ArrayList<Relation>();
		if (null != record && null != record.get(DEFAULT_CYPHER_NODE_OBJECT) && null != record.get(DEFAULT_CYPHER_RELATION_OBJECT)) {
			org.neo4j.driver.v1.types.Node node = record.get(DEFAULT_CYPHER_NODE_OBJECT).asNode();
			Long id = node.id();
		}
		return outRelationships;
	}

}
