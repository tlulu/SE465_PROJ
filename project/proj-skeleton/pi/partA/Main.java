import java.util.HashMap;
import java.util.Scanner;

public class Main {

	public final static String FUNCTION_IDENTIFIER = "Call graph node for function: '";
	public final static String NULL_FUNCTION_IDENTIFIER = "Call graph node <<null function>>";
	public final static String CALLEE_IDENTIFIER = "calls function '";
	public final static String EXTERNAL_NODE_IDENTIFIER = "calls external node";

	private static class Node {
		String id;
		HashMap<String, Node> callees;

		public Node(String id) {
			this.id = id;
			this.callees = new HashMap<String, Node>();
		}
	}

	static int t_support;
	static int t_confidence;
	static HashMap<String, Node> graph;
	static HashMap<String, Node> allNodes;

	public static void printGraph() {
		System.out.println("Graph: ");
		for (Node node : graph.values()) {
			System.out.println("Function: " + node.id);
			for (Node callee : node.callees.values()) {
				System.out.println("\tcalls " + callee.id);
			}
		}
	}

	public static void main(String[] args) {
		t_support = Integer.parseInt(args[0]);
		t_confidence = Integer.parseInt(args[1]);

		graph = new HashMap<String, Node>();
		allNodes = new HashMap<String, Node>();

		Node curNode = null;

		boolean isNullFunction = false;

		Scanner sc = new Scanner(System.in);
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			if (line.length() > 0) {
				if (line.contains(FUNCTION_IDENTIFIER)) {
					String id = line.substring(FUNCTION_IDENTIFIER.length(), line.indexOf("'", FUNCTION_IDENTIFIER.length()));
					curNode = allNodes.get(id);
					if (curNode == null) {
						curNode = new Node(id);
						allNodes.put(id, curNode);
					}
					graph.put(id, curNode);
					isNullFunction = false;
				} 
				else if (line.contains(NULL_FUNCTION_IDENTIFIER)) {
					isNullFunction = true;
				}
				else if (line.contains(CALLEE_IDENTIFIER) && !isNullFunction) {
					int fromIndex = line.indexOf(CALLEE_IDENTIFIER) + CALLEE_IDENTIFIER.length(); 
					String calleeID = line.substring(fromIndex, line.indexOf("'", fromIndex));
					Node calleeNode = allNodes.get(calleeID);
					if (calleeNode == null) {
						calleeNode = new Node(calleeID);
						allNodes.put(calleeID, calleeNode);
					}
					curNode.callees.put(calleeID, calleeNode);
				}
			}			

		}
		
		printGraph();	
		

	}
}
