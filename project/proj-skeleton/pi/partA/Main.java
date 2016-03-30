import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Scanner;
import java.util.TreeMap;

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

	// Represents a single method in the call graph and its support.
	private static class SupportSingle {
		String id;
		int supportValue;

		public SupportSingle(String id) {
			this.id = id;
			this.supportValue = 0;
		}
	}

	// Represents a pair of methods in the call graph and their support and confidence values.
	private static class SupportPair {
		String id1;
		String id2;
		int supportValue;
		double confidence1;
		double confidence2;

		public SupportPair(String id1, String id2) {
			// Ensure that id1 < id2
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
			// Ensure that the lesser of the two ids comes first
			// Format is: "id1 id2"
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
	
	// Store caller method nodes only
	static HashMap<String, Node> graph;
	// Store both caller and callee method nodes
	static HashMap<String, Node> allNodes;

	static HashMap<String, SupportSingle> supportSingles;
	static HashMap<String, SupportPair> supportPairs;

	// Store sorted list of callers for each callee
	static HashMap<String, TreeMap<String, Boolean> > calleeToCallers;
	// Store the support pairs whose support and confidence pass the thresholds
	static ArrayList<SupportPair> pairsThatMatter;

	// Debugging tool
	public static void printGraph() {
		System.err.println("Graph: ");
		for (Node node : graph.values()) {
			System.err.println("Function: " + node.id);
			for (Node callee : node.callees.values()) {
				System.err.println("\tcalls " + callee.id);
			}
		}
	}

	// Debugging tool
	public static void printSupports() {
		for (SupportSingle supportSingle : supportSingles.values()) {
			System.err.println("SUPP_SINGLE : " + supportSingle.id + "=" + supportSingle.supportValue);
		}
		
		for (SupportPair supportPair : supportPairs.values()) {
			System.err.println("SUPP_PAIR : (" + supportPair.id1 + "," + supportPair.id2 + ")=" + supportPair.supportValue);
		}

	}

	// Calculate the support values for each method and pair of methods
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

	// Calculate the confidence values for each pair of methods that are called together at least once
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

	// Print out the bugs - cases where a support pair passing the thresholds doesn't call both
	// methods in the same scope. Iterate through the callers for both callees and since they're
	// sorted, treat it like merging in mergesort to find differences between the two lists of callers
	public static void printBugs() {
		for (SupportPair pair : pairsThatMatter) {
			Set<String> keySetOne = calleeToCallers.get(pair.id1).keySet();
			Set<String> keySetTwo = calleeToCallers.get(pair.id2).keySet();
			String[] callersForOne = keySetOne.toArray(new String[keySetOne.size()]);
			String[] callersForTwo = keySetTwo.toArray(new String[keySetTwo.size()]);
			int i=0, j=0;
			while (i < callersForOne.length && j < callersForTwo.length) {
				String one = callersForOne[i];
				String two = callersForTwo[j];

				int compareValue = one.compareTo(two);
				if (compareValue > 0) {
					if (pair.confidence2 >= t_confidence) {
						System.out.println(pair.bugReportString(false, two));
					}
					j++;
				}
				else if (compareValue < 0) {
					if (pair.confidence1 >= t_confidence) {
						System.out.println(pair.bugReportString(true, one));
					}
					i++;
				}
				else {
					i++;
					j++;
				}
			}
			while (i < callersForOne.length) {
				String one = callersForOne[i];
				if (pair.confidence1 >= t_confidence) {
					System.out.println(pair.bugReportString(true, one));
				}
				i++;
			}
			while (j < callersForTwo.length) {
				String two = callersForTwo[j];
				if (pair.confidence2 >= t_confidence) {
					System.out.println(pair.bugReportString(false, two));
				}
				j++;
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
			// Build the graph here - each line either represents a new caller function,
			// a null function, a callee method being called by the current caller, or
			// an external function being called by the current caller
			// Only keep track of the non-null caller functions and non-external callee methods
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
		
		// Print graph to stderr for debugging
		printGraph();	

		supportSingles = new HashMap<String, SupportSingle>();
		supportPairs = new HashMap<String, SupportPair>();

		buildSupports();

		// Print supports list to stderr for debugging
		printSupports();
		
		buildConfidences();

		printBugs();	
	}
}
