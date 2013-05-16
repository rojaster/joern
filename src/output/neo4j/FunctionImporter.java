package output.neo4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.RelationshipType;

import astnodes.ASTNode;
import astnodes.functionDef.FunctionDef;
import cfg.BasicBlock;
import cfg.CFG;

public class FunctionImporter
{
	GraphNodeStore nodeStore = new GraphNodeStore();
	String filename;
	
	ASTImporter astImporter = new ASTImporter();
	CFGImporter cfgImporter = new CFGImporter();
	
	public void addFunctionToDatabaseSafe(FunctionDef node)
	{
		try{
			Function function = new Function(node);
			function.setFilename(filename);
			addFunctionToDatabase(function);
		}catch(RuntimeException ex)
		{
			// ex.printStackTrace();
			System.err.println("Error indexing function: " + node.name.getEscapedCodeStr());
			return;
		}
	}

	private void addFunctionToDatabase(Function function)
	{
		astImporter.addASTToDatabase(function.getASTRoot());
		cfgImporter.addCFGToDatabase(function.getCFG());
		addFunctionNode(function);
	}
	
	private void addFunctionNode(Function function)
	{
		Map<String, Object> properties = createPropertiesForFunction(function);
		
		long thisId = nodeStore.addNeo4jNode(function, properties);
		
		// index, but do not index location
		properties.remove("location");
		nodeStore.indexNode(thisId, properties);
			
		linkFunctionWithRootASTNode(thisId, function.getASTRoot());
		linkFunctionWithAllASTNodes(thisId, function.getASTRoot());
		
		CFG cfg = function.getCFG();
		if(cfg != null)
			linkFunctionWithAllCFGNodes(thisId, cfg);
	}

	private Map<String, Object> createPropertiesForFunction(Function function)
	{
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("type", "Function");
		properties.put("filename", function.getFilename());
		properties.put("signature", function.getSignature());
		properties.put("location", function.getLocation());
		properties.put("functionName", function.getName());
		return properties;
	}
	
	private void linkFunctionWithRootASTNode(long thisId, ASTNode astRoot)
	{

		RelationshipType rel = DynamicRelationshipType.withName(EdgeTypes.IS_FUNCTION_OF_AST_ROOT);
		long dstId = astRoot.id;
		Neo4JDatabase.addRelationship(thisId, dstId, rel, null);
	}

	private void linkFunctionWithAllASTNodes(long thisId, ASTNode node)
	{
		linkParentWithASTNode(thisId, node);
		
		final int nChildren = node.getChildCount();
		for(int i = 0; i < nChildren; i++){
			ASTNode child = node.getChild(i);
			linkFunctionWithAllASTNodes(thisId, child);
		}
	}

	private void linkFunctionWithAllCFGNodes(long thisId, CFG cfg)
	{
		Vector<BasicBlock> basicBlocks = cfg.getBasicBlocks();
		Iterator<BasicBlock> it = basicBlocks.iterator();
		while(it.hasNext()){
			BasicBlock block = it.next();
			linkFunctionWithCFGNode(thisId, block);
		}
	}
	
	private void linkFunctionWithCFGNode(long functionId, BasicBlock block)
	{
		RelationshipType rel = DynamicRelationshipType.withName(EdgeTypes.IS_FUNCTION_OF_BASIC_BLOCK);
		long dstId = block.id;
		Neo4JDatabase.addRelationship(functionId, dstId, rel, null);
	}

	private void linkParentWithASTNode(long thisId, ASTNode node)
	{
		RelationshipType rel = DynamicRelationshipType.withName(EdgeTypes.IS_FUNCTION_OF_AST_NODE);
		long dstId = node.id;
		Neo4JDatabase.addRelationship(thisId, dstId, rel, null);
	}

	public void setFilename(String aFilename)
	{
		filename = aFilename;
	}
}
