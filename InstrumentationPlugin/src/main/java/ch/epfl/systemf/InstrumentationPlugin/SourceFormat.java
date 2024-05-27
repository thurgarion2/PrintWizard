package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LineMap;
import com.sun.tools.javac.file.PathFileObject;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
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
    public record SourceFormatDescription(SourceFile sourceFile,
                                          Map<String, NodeSourceFormat> syntaxNodes) implements JsonSerializable {

        /********
         **** SourceFormatDescription methods
         ********/


        @Override
        public JSONObject toJson() {
            return new JSONObject(Map.of(
                    "sourceFile", sourceFile.toJson(),
                    "syntaxNodes", new JSONObject(syntaxNodes
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(entry -> entry.getValue().identifier(), entry -> entry.getValue().toJson()))
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

    public record AbsentFromSourceCode() implements NodeSourceFormat {

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
    public record PresentInSourceCode(List<NodeSourceFormat> children,
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
                    "startLine",  startLine,
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
    private final Map<String, NodeSourceFormat> cache;


    public SourceFormat(JCTree.JCCompilationUnit compilationUnit) {
        this.treeNodeToPos = new TreeNodeToPos(compilationUnit.getLineMap(), compilationUnit.endPositions);

        ExpressionTree packageName = compilationUnit.getPackageName();
        this.sourceFile = new SourceFormatDescription.SourceFile(
                packageName == null ? "" : packageName.toString(),
                compilationUnit.getSourceFile().getName()
        );


        this.cache = new HashMap<>();


        this.source = new SourceCode(applyWithAssert(compilationUnit.getSourceFile(), (path) -> {
            try {
                return path.getCharContent(false);
            } catch (IOException e) {
                throw new IllegalArgumentException();
            }
        }),
                compilationUnit.getLineMap());
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
        return nodeId(node, List.of());
    }

    // children should be inside the node
    public NodeSourceFormat nodeId(JCTree node, List<? extends JCTree> children) {

        return switch (treeNodeToPos.pos(node)) {
            case TreeNodeToPos.NotInFile ignored -> new AbsentFromSourceCode();
            case TreeNodeToPos.TreePos pos -> {
                String key = nodeKey(pos);
                if (cache.containsKey(key)) {
                    yield cache.get(key);
                }


                List<PresentInSourceCode.Token> expression = new ArrayList<>();
                Stream<LineOrChild> childRanges = IntStream.range(0, children.size())
                        .mapToObj(i -> switch (treeNodeToPos.pos(children.get(i))) {
                            case TreeNodeToPos.NotInFile ignored -> throw new IllegalArgumentException();
                            case TreeNodeToPos.TreePos p -> {
                                Assert.assertThat(pos.startLine <= p.startLine && pos.endLine >= p.endLine);
                                yield new NodeChild(new SourceCode.Range(p.startSourceIndex, p.endSourceIndex), i);
                            }
                        });
                Stream<LineOrChild> lines = IntStream.range(pos.startLine + 1, pos.endLine+1)
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
                addText(expression, pos.endSourceIndex, endLineRange.end);


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
                cache.put(key, nodeFormat);
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

    /********
     **** dump NodeFormat to file
     ********/


    public void end() {
        SourceFormatDescription description = new SourceFormatDescription(sourceFile, cache);
        try (
                FileWriter f = new FileWriter("source_format.json");
                BufferedWriter writer = new BufferedWriter(f)
        ) {
            description.toJson().write(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**************
     ********* translate tree node to a position in the source file
     **************/

    private record TreeNodeToPos(LineMap lineMap, EndPosTable endPosTable) {

        public Pos pos(JCTree tree) {
            int startIndex = tree.getStartPosition();
            int endIndex = endPosTable.getEndPos(tree);

            if (startIndex != Position.NOPOS && endIndex != Position.NOPOS && startIndex != endIndex) {

                return new TreePos(
                        (int) lineMap.getLineNumber(startIndex),
                        (int) lineMap.getColumnNumber(startIndex),
                        startIndex,
                        (int) lineMap.getLineNumber(endIndex),
                        (int) lineMap.getColumnNumber(endIndex),
                        endIndex
                );
            } else {
                return new NotInFile();
            }
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

}
