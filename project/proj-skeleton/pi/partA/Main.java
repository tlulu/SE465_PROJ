import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;

// TODO: Stop using the following:
//	a) String.compareTo()
//	b) String.equals()

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

	private static class SupportSingle {
		String id;
		int supportValue;

		public SupportSingle(String id) {
			this.id = id;
			this.supportValue = 0;
		}
	}

	private static class SupportPair {
		String id1;
		String id2;
		int supportValue;
		double confidence1;
		double confidence2;

		public SupportPair(String id1, String id2) {
			if (id1.compareTo(id2) < 0) {
				this.id1 = id1;
				this.id2 = id2;
			}
			else {
				this.id1 = id2;
				this.id2 = id1;
			}
			
			this.supportValue = 0;
			this.confidence1 = 0.0;
			this.confidence2 = 0.0;
		}

		public static String getPairID(String id1, String id2) {
			String pairID;
			
			if (id1.compareTo(id2) < 0) {
				pairID = id1 + " " + id2;
			}
			else {
				pairID = id2 + " " + id1; 
			}

			return pairID;
		}

		public String bugReportString(boolean isId1, String scope) {
			String formatString = "bug: %s in %s, pair: (%s, %s), support: %d, confidence: %.2f%%";
			if (isId1) {
				return String.format(formatString, id1, scope, id1, id2, supportValue, confidence1 * 100);
			}
			else {
				return String.format(formatString, id2, scope, id1, id2, supportValue, confidence2 * 100);
			}
		} 
	}

	static int t_support;
	static double t_confidence;
	
	static HashMap<String, Node> graph; // Caller -> Callees
	static HashMap<String, Node> allNodes;

	static HashMap<String, SupportSingle> supportSingles;
	static HashMap<String, SupportPair> supportPairs;

	static HashMap<String, TreeMap<String, Boolean> > calleeToCallers;
	static ArrayList<SupportPair> pairsThatMatter;

	public static void printGraph() {
		System.err.println("Graph: ");
		for (Node node : graph.values()) {
			System.err.println("Function: " + node.id);
			for (Node callee : node.callees.values()) {
				System.err.println("\tcalls " + callee.id);
			}
		}
	}

	public static void printSupports() {
		for (SupportSingle supportSingle : supportSingles.values()) {
			System.err.println("SUPP_SINGLE : " + supportSingle.id + "=" + supportSingle.supportValue);
		}
		
		for (SupportPair supportPair : supportPairs.values()) {
			System.err.println("SUPP_PAIR : (" + supportPair.id1 + "," + supportPair.id2 + ")=" + supportPair.supportValue);
		}

	}

	public static void buildSupports() {
		for (Node node : graph.values()) {
			ArrayList<String> visitedCallees = new ArrayList<String>();
			for (Node callee : node.callees.values()) {
				SupportSingle supportSingle = supportSingles.get(callee.id);
				if (supportSingle == null) {
					supportSingle = new SupportSingle(callee.id);
					supportSingles.put(callee.id, supportSingle);
				}			
				supportSingle.supportValue++;
				
				for (String visitedCalleeID : visitedCallees) {
					String pairID = SupportPair.getPairID(callee.id, visitedCalleeID);

					SupportPair supportPair = supportPairs.get(pairID);
					if (supportPair == null) {
						supportPair = new SupportPair(callee.id, visitedCalleeID);
						supportPairs.put(pairID, supportPair);
					}
					supportPair.supportValue++;
				}
				
				visitedCallees.add(callee.id);
			}
		}	
	}

	public static void buildConfidences() {
		for (SupportPair supportPair : supportPairs.values()) {
			supportPair.confidence1 = (double)supportPair.supportValue / supportSingles.get(supportPair.id1).supportValue;
			supportPair.confidence2 = (double)supportPair.supportValue / supportSingles.get(supportPair.id2).supportValue;


			if (supportPair.supportValue >= t_support && (supportPair.confidence1 >= t_confidence || supportPair.confidence2 >= t_confidence)) {
				pairsThatMatter.add(supportPair);
			}

			System.err.println("CONFIDENCE (" + supportPair.id1 + "," + supportPair.id2 + ")/(" + supportPair.id1 + ") = " + supportPair.confidence1);
			System.err.println("CONFIDENCE (" + supportPair.id1 + "," + supportPair.id2 + ")/(" + supportPair.id2 + ") = " + supportPair.confidence2);

			
		}
	}

	public static void printBugs() {
		for (SupportPair pair : supportPairs.values()) {
			Iterator<String> callersForOne = calleeToCallers.get(pair.id1).keySet().iterator();
			Iterator<String> callersForTwo = calleeToCallers.get(pair.id2).keySet().iterator();
			String one = null;
			String two = null;
			boolean iterateOne = true;
			boolean iterateTwo = true;
			while (callersForOne.hasNext() && callersForTwo.hasNext()) {
				// TODO: This is disappointing - stop using compareTo
				if (iterateOne) {
					one = callersForOne.next();
				}
				if (iterateTwo) {
					two = callersForTwo.next();
				}
				int compareValue = one.compareTo(two);
				if (compareValue > 0) {
					if (pair.confidence2 >= t_confidence) {
						System.out.println(pair.bugReportString(false, two));
					}
					iterateOne = false;
					iterateTwo = true;
				}
				else if (compareValue < 0) {
					if (pair.confidence1 >= t_confidence) {
						System.out.println(pair.bugReportString(true, one));
					}
					iterateOne = true;
					iterateTwo = false;
				}
				else {
					iterateOne = true;
					iterateTwo = true;
				}
			}
			while (callersForOne.hasNext() && pair.confidence1 >= t_confidence) {
				one = callersForOne.next();
				System.out.println(pair.bugReportString(true, one));
			}
			while (callersForTwo.hasNext() && pair.confidence2 >= t_confidence) {
				two = callersForTwo.next();
				System.out.println(pair.bugReportString(false, two));
			}
		}
	}

	public static void main(String[] args) {
		t_support = Integer.parseInt(args[0]);
		t_confidence = Integer.parseInt(args[1]) / 100.0;

		graph = new HashMap<String, Node>();
		allNodes = new HashMap<String, Node>();

		calleeToCallers = new HashMap<String, TreeMap<String, Boolean> >();
		pairsThatMatter = new ArrayList<SupportPair>();

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
					
					// Insert caller to calleeToCallers map
					TreeMap<String, Boolean> callers = calleeToCallers.get(calleeID);
					if (callers == null) {
						callers = new TreeMap<String, Boolean>();
						calleeToCallers.put(calleeID, callers);
					}
					callers.put(curNode.id, true);
					
					// Insert callee as neighbor of caller in graph
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

		supportSingles = new HashMap<String, SupportSingle>();
		supportPairs = new HashMap<String, SupportPair>();

		buildSupports();

		printSupports();
		
		buildConfidences();

		printBugs();	
	}
}
