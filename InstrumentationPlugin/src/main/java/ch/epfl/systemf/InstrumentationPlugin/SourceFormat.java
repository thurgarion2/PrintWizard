package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LineMap;
import com.sun.tools.javac.file.PathFileObject;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Position;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SourceFormat {

    // we will move interface to another file after
    // every class that we serialize should implement it
    interface JsonSerializable {
        JSONObject toJson();
    }

    /*******************************************************
     **************** Data structure ******************
     *******************************************************/

    /**************
     ********* SourceFormatDescription
     **************/

    /********
     **** fields
     ********/
    private record SourceFormatDescription(SourceFile sourceFile,
                                           Set<NodeSourceFormat> syntaxNodes) implements JsonSerializable {

        /********
         **** SourceFormatDescription methods
         ********/


        @Override
        public JSONObject toJson() {
            return new JSONObject(Map.of(
                    "sourceFile", sourceFile.toJson(),
                    "syntaxNodes", new JSONObject(syntaxNodes
                            .stream()
                            .collect(Collectors.toMap(NodeSourceFormat::identifier, JsonSerializable::toJson))
                    )
            ));
        }

        /********
         **** file Id
         ********/
        public record SourceFile(String packageName, String fileName) implements JsonSerializable {

            public SourceFile(String packageName, String fileName) {
                this.packageName = packageName;
                this.fileName = fileName;
                checkInvariant();
            }

            private void checkInvariant() {
                Assert.assertThat(!fileName.isEmpty());
            }

            public String identifier() {
                return fileName;
            }

            @Override
            public JSONObject toJson() {
                return new JSONObject(Map.of("packageName", packageName, "fileName", fileName));
            }
        }
    }

    /**************
     ********* NodeSourceFormat
     **************/

    public sealed interface NodeSourceFormat extends JsonSerializable {
        String identifier();
    }

    /********
     **** was not defined in source code
     ********/

    private record AbsentFromSourceCode() implements NodeSourceFormat {

        @Override
        public JSONObject toJson() {
            return new JSONObject(Map.of("kind", "absent", "identifier", identifier()));
        }

        @Override
        public String identifier() {
            return "absent";
        }
    }

    /********
     **** Present in source code
     ********/

    //startIndex and endIndex are defined as line:col and should uniquely identify a node
    private record PresentInSourceCode(List<NodeSourceFormat> children,
                                       SourceFormatDescription.SourceFile sourceFile,
                                       int startLine,
                                       int endLine,
                                       Expression expression,
                                       Text prefix,
                                       Text suffix,
                                       String startIndex,
                                       String endIndex) implements NodeSourceFormat, JsonSerializable {
        /********
         **** NodeSourceFormat constructor
         ********/

        public PresentInSourceCode(List<NodeSourceFormat> children,
                                   SourceFormatDescription.SourceFile sourceFile,
                                   int startLine,
                                   int endLine,
                                   Expression expression,
                                   Text prefix,
                                   Text suffix,
                                   String startIndex,
                                   String endIndex) {
            this.children = Collections.unmodifiableList(children);
            this.sourceFile = sourceFile;
            this.startLine = startLine;
            this.endLine = endLine;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.expression = expression;
            this.prefix = prefix;
            this.suffix = suffix;
            checkInvariant();
        }

        private void checkInvariant() {
            Assert.assertThat(!Objects.equals(startIndex, endIndex));
            int nbChildren = children.size();
            Assert.assertThat(expression.children.stream()
                    .filter(t -> t instanceof Child)
                    .allMatch(child -> ((Child) child).childIndex < nbChildren && ((Child) child).childIndex >= 0));
        }

        /********
         **** NodeSourceFormat instance methods
         ********/


        public String identifier() {
            return sourceFile.identifier() + "-" + startIndex + "-" + endIndex;
        }

        @Override
        public JSONObject toJson() {
            return new JSONObject(Map.of(
                    "children", new JSONArray(children.stream().map(NodeSourceFormat::identifier).toList()),
                    "sourceFile", sourceFile.toJson(),
                    "identifier", identifier(),
                    "startLine", startLine,
                    "endLine", endLine,
                    "expression", expression.toJson(),
                    "suffix", suffix.toJson(),
                    "prefix", prefix.toJson(),
                    "kind", "presentInSourceCode"
            ));
        }


        /********
         **** token definition
         ********/

        public record Expression(List<Token> children) implements JsonSerializable {
            @Override
            public JSONObject toJson() {
                return new JSONObject(Map.of(
                        "kind", "expression",
                        "tokens", new JSONArray(children.stream().map(JsonSerializable::toJson).toList())
                ));
            }
        }


        public sealed interface Token extends JsonSerializable {
        }


        public record Text(String text) implements Token {

            @Override
            public JSONObject toJson() {
                return new JSONObject(Map.of("kind", "Text", "text", text));
            }
        }

        // index refer to children list
        public record Child(int childIndex, String text) implements Token {
            @Override
            public JSONObject toJson() {
                return new JSONObject(Map.of(
                        "kind", "Child",
                        "childIndex", childIndex,
                        "text", text));
            }
        }

        public record LineStart() implements Token {
            @Override
            public JSONObject toJson() {
                return new JSONObject(Map.of("kind", "LineStart"));
            }
        }

    }


    /*******************************************************
     **************** Transform JCCompilationUnit to SourceFormat ******************
     *******************************************************/

    /**************
     ********* fields
     **************/

    private final TreeNodeToPos treeNodeToPos;
    private final SourceCode source;
    private final SourceFormatDescription.SourceFile sourceFile;

    // for the cache we could use start and end information
    private final Map<JCTree, NodeSourceFormat> preComputed;


    public SourceFormat(JCTree.JCCompilationUnit compilationUnit) {

        ExpressionTree packageName = compilationUnit.getPackageName();
        this.sourceFile = new SourceFormatDescription.SourceFile(
                packageName == null ? "" : packageName.toString(),
                compilationUnit.getSourceFile().getName()
        );


        this.source = new SourceCode(applyWithAssert(compilationUnit.getSourceFile(), (path) -> {
            try {
                return path.getCharContent(false);
            } catch (IOException e) {
                throw new IllegalArgumentException();
            }
        }),
                compilationUnit.getLineMap());

        this.treeNodeToPos = new TreeNodeToPos(compilationUnit.getLineMap(), compilationUnit.endPositions, source);
        this.preComputed = new HashMap<>();
        CollectNodeFormat formatCollector = new CollectNodeFormat();
        formatCollector.scan(compilationUnit);

        SourceFormatDescription description = new SourceFormatDescription(
                sourceFile,
                new HashSet<>(preComputed.values()));
        try (
                FileWriter f = new FileWriter("source_format.json");
                BufferedWriter writer = new BufferedWriter(f)
        ) {
            description.toJson().write(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static CharSequence applyWithAssert(JavaFileObject o, Function<PathFileObject, CharSequence> f) {
        if (o instanceof PathFileObject path) {
            return f.apply(path);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**************
     ********* API
     **************/

    /********
     **** create node id
     ********/
    public NodeSourceFormat nodeId(JCTree node) {
        //TODO handle literal when inside function calls
        if (!preComputed.containsKey(node))
            System.out.println(node.getClass().getName());

        return preComputed.getOrDefault(node, new AbsentFromSourceCode());
    }

    // children should be inside the node
    private NodeSourceFormat computeNodeId(JCTree node, List<? extends JCTree> children) {


        return switch (treeNodeToPos.pos(node)) {
            case TreeNodeToPos.NotInFile ignored -> new AbsentFromSourceCode();
            case TreeNodeToPos.StartPos pos -> {
                String repr = node.toString();
                SourceCode.Range startLineRange = source.line(pos.startLine);

                NodeSourceFormat nodeFormat = new PresentInSourceCode(
                        children.stream().map(this::nodeId).toList(),
                        sourceFile,
                        pos.startLine,
                        pos.startLine,
                        new PresentInSourceCode.Expression(List.of(new PresentInSourceCode.Text(repr))),
                        new PresentInSourceCode.Text(source.fromTo(new SourceCode.Range(startLineRange.start, pos.startSourceIndex))),
                        new PresentInSourceCode.Text(""),
                        pos.startLine + ":" + pos.startCol,
                        pos.startLine + ":" + pos.startCol  + repr.length()
                );
                preComputed.put(node, nodeFormat);
                yield nodeFormat;
            }
            case TreeNodeToPos.TreePos pos -> {

                List<PresentInSourceCode.Token> expression = new ArrayList<>();
                Stream<LineOrChild> childRanges = IntStream.range(0, children.size())
                        .mapToObj(i -> switch (treeNodeToPos.pos(children.get(i))) {
                            case TreeNodeToPos.NotInFile ignored -> throw new IllegalArgumentException();
                            case TreeNodeToPos.StartPos p -> new NodeChild(new SourceCode.Range(p.startSourceIndex, p.startSourceIndex+children.get(i).toString().length()), i);
                            case TreeNodeToPos.TreePos p -> {
                                Assert.assertThat(pos.startLine <= p.startLine && pos.endLine >= p.endLine);
                                yield new NodeChild(new SourceCode.Range(p.startSourceIndex, p.endSourceIndex), i);
                            }
                        });
                Stream<LineOrChild> lines = IntStream.range(pos.startLine + 1, pos.endLine + 1)
                        .mapToObj(lineNumber -> new NodeLine(source.line(lineNumber).start, lineNumber));


                // we should assert that no lines start at the same time as tokens
                List<LineOrChild> linesAndRanges = Stream.concat(childRanges, lines)
                        .sorted(Comparator.comparingInt(LineOrChild::startSourceIndex))
                        .toList();

                int start = pos.startSourceIndex;
                for (LineOrChild x : linesAndRanges) {
                    switch (x) {
                        case NodeLine l:
                            addText(expression, start, l.sourceIndex);
                            start = l.sourceIndex + 1;
                            expression.add(new PresentInSourceCode.LineStart());
                            break;
                        case NodeChild child:
                            addText(expression, start, child.range.start);
                            start = child.range.end;
                            expression.add(new PresentInSourceCode.Child(child.childIndex, source.fromTo(child.range)));
                            break;
                    }
                }

                addText(expression, start, pos.endSourceIndex);

                //suffix
                SourceCode.Range startLineRange = source.line(pos.startLine);
                SourceCode.Range endLineRange = source.line(pos.endLine);



                NodeSourceFormat nodeFormat = new PresentInSourceCode(
                        children.stream().map(this::nodeId).toList(),
                        sourceFile,
                        pos.startLine,
                        pos.endLine,
                        new PresentInSourceCode.Expression(expression),
                        new PresentInSourceCode.Text(source.fromTo(new SourceCode.Range(startLineRange.start, pos.startSourceIndex))),
                        new PresentInSourceCode.Text(source.fromTo(new SourceCode.Range(pos.endSourceIndex, endLineRange.end))),
                        pos.startLine + ":" + pos.startCol,
                        pos.endLine + ":" + pos.endCol
                );
                preComputed.put(node, nodeFormat);
                yield nodeFormat;
            }
        };

    }

    /********
     **** node id helper
     ********/

    private static String nodeKey(TreeNodeToPos.TreePos nodePos) {
        return nodePos.startLine + ":" + nodePos.startCol + "-" + nodePos.endLine + ":" + nodePos.endCol;
    }

    private sealed interface LineOrChild {
        int startSourceIndex();
    }

    private record NodeLine(int sourceIndex, int line) implements LineOrChild {
        @Override
        public int startSourceIndex() {
            return sourceIndex;
        }
    }

    private record NodeChild(SourceCode.Range range, int childIndex) implements LineOrChild {
        @Override
        public int startSourceIndex() {
            return range.start;
        }
    }


    private void addText(List<PresentInSourceCode.Token> tokens, int start, int end) {
        if (start < end) {
            tokens.add(new PresentInSourceCode.Text(source.fromTo(new SourceCode.Range(start, end))));
        }

    }


    /**************
     ********* translate tree node to a position in the source file
     **************/

    private record TreeNodeToPos(LineMap lineMap, EndPosTable endPosTable, SourceCode source) {

        public Pos pos(JCTree tree) {
            int startIndex = tree.getStartPosition();
            int endIndex = endPosTable.getEndPos(tree);

            // we don't have end position information for some nodes
            if (startIndex == Position.NOPOS)
                return new NotInFile();
            if (endIndex == Position.NOPOS) {
                if (tree.toString().contains("super")) {
                    return new NotInFile();
                } else {
                    return new StartPos((int) lineMap.getLineNumber(startIndex),
                            (int) lineMap.getColumnNumber(startIndex),
                            startIndex);
                }
            }


            return new TreePos(
                    (int) lineMap.getLineNumber(startIndex),
                    (int) lineMap.getColumnNumber(startIndex),
                    startIndex,
                    (int) lineMap.getLineNumber(endIndex),
                    (int) lineMap.getColumnNumber(endIndex),
                    endIndex);

        }

        private int startLine(JCTree tree) {
            return (int) lineMap.getLineNumber(tree.getStartPosition());
        }

        private int endLine(JCTree tree) {
            return (int) lineMap.getLineNumber(tree.getEndPosition(endPosTable));
        }


        sealed interface Pos {
        }

        // either a node exist and we have start and end information or don't and we have nothing
        public record TreePos(int startLine, int startCol, int startSourceIndex, int endLine, int endCol,
                              int endSourceIndex) implements Pos {
        }

        public record StartPos(int startLine, int startCol, int startSourceIndex) implements Pos {
        }

        public record NotInFile() implements Pos {
        }
    }

    /**************
     ********* access source code information
     **************/

    public record SourceCode(CharSequence sourceCode, LineMap lineMap) {
        String fromTo(Range range) {
            return sourceCode.subSequence(range.start, range.end).toString();
        }

        Range line(int line) {
            int lineStart = (int) lineMap.getStartPosition(line);
            int lineEnd = 0;
            //ugly but didn't find a way to get the number of lines
            try {
                lineEnd = (int) lineMap.getStartPosition(line + 1);
            } catch (IndexOutOfBoundsException e) {
                lineEnd = sourceCode.length();
            }
            return new Range(lineStart, lineEnd);
        }

        // inclusive, exclusive
        public record Range(int start, int end) {
            public Range(int start, int end) {
                this.start = start;
                this.end = end;
                Assert.assertThat(start <= end);
            }
        }
    }

    /*******************************************************
     **************** visit tree and collect source format ******************
     *******************************************************/

// we do it before starting to transform the tree, otherwise it is a mess
    private class CollectNodeFormat extends TreeScanner {

        @Override
        public void visitBinary(JCTree.JCBinary tree) {
            super.visitBinary(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitUnary(JCTree.JCUnary tree) {
            super.visitUnary(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitAssign(JCTree.JCAssign tree) {
            super.visitAssign(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitTypeTest(JCTree.JCInstanceOf tree) {
            super.visitTypeTest(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl tree) {
            super.visitVarDef(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitApply(JCTree.JCMethodInvocation tree) {
            super.visitApply(tree);
            preComputed.put(tree, computeNodeId(tree, tree.args));
        }

        @Override
        public void visitNewClass(JCTree.JCNewClass tree) {
            super.visitNewClass(tree);
            preComputed.put(tree, computeNodeId(tree, tree.args));
        }

        @Override
        public void visitAssert(JCTree.JCAssert tree) {
            super.visitAssert(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            super.visitIdent(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitReturn(JCTree.JCReturn tree) {
            super.visitReturn(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }

        @Override
        public void visitAssignop(JCTree.JCAssignOp tree) {
            super.visitAssignop(tree);
            preComputed.put(tree, computeNodeId(tree, List.of()));
        }
    }

}
