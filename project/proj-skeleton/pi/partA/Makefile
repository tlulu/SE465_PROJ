default:
	javac *.java

run:
	java -Xms128m -Xmx128m Main 3 65 < foo.callgraph

test:
	java -Xms128m -Xmx128m Main 3 65 < foo.callgraph 1> foo.out 2>/dev/null

clean:
	rm *.class
