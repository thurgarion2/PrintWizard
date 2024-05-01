package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.LineMap;
import com.sun.tools.javac.file.PathFileObject;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import org.json.JSONObject;

import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class NodeIdFactory {

    /*******************************************************
     **************** NodeId ******************
     *******************************************************/

    public record NodeId(int nodeId, int line, String sourceFileName) {
        public String identifier(){
            return nodeId+"-"+line;
        }

    }


    /*******************************************************
     **************** Generate NodeId ******************
     *******************************************************/

    /**************
     ********* Inner data
     **************/

    private static final String fileName = "source_format.json";
    private final JSONObject nodesData = new JSONObject();
    private final LineMap lineMap;
    private final EndPosTable endPosTable;
    private final CharSequence sourceCode;
    private final String sourceFileName;
    private int nodeCounter = 0;
    private Map<JCTree, Integer> treeToId;
    private JSONObject formatData = new JSONObject();
    public final NodeId mutipleLineNode = new NodeId(++nodeCounter, -1, "");


    public NodeIdFactory(JCTree.JCCompilationUnit compilationUnit){
        this.lineMap = compilationUnit.getLineMap();
        this.endPosTable = compilationUnit.endPositions;
        this.sourceFileName = compilationUnit.getSourceFile().getName();
        this.treeToId = new HashMap<>();


        this.sourceCode = applyWithAssert(compilationUnit.getSourceFile(), (path)-> {
            try {
                return path.getCharContent(false);
            } catch (IOException e) {
                throw new IllegalArgumentException();
            }
        });
    }

    private static CharSequence applyWithAssert(JavaFileObject o, Function<PathFileObject, CharSequence> f){
        if(o instanceof PathFileObject path){
            return f.apply(path);
        }else{
            throw new IllegalArgumentException();
        }
    }

    /**************
     ********* API
     **************/

    public NodeId nodeId(JCTree node){
        if(!treeToId.containsKey(node)){
            treeToId.put(node, ++nodeCounter);
            NodeId id =  new NodeId(treeToId.get(node), startLine(node), sourceFileName);
            formatData.put(id.identifier(), nodeFormat(node));

            return id;
        }
        return new NodeId(treeToId.get(node), startLine(node), sourceFileName);
    }


    public void end() {
        try(Writer writer = new FileWriter(fileName);
            BufferedWriter buffered = new BufferedWriter(writer)) {
            formatData.write(buffered);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**************
     ********* NodeID to Json
     **************/

    private JSONObject nodeFormat(JCTree node){
        int startLine = startLine(node);
        int endLine = endLine(node);

        if(endLine==0)
            return new JSONObject(Map.of("info", "don't exist"));

        if(startLine!=endLine)
            throw new UnsupportedOperationException();

        return new JSONObject(Map.of(
                "line", line(startLine),
                "lineNumber", startLine,
                "startCol",  startCol(node),
                "endCol", endCol(node)
        ));
    }

    /*******************************************************
     **************** Extract source information ******************
     *******************************************************/


    //first column is 1
    private int startCol(JCTree tree){
        return (int)lineMap.getColumnNumber(tree.getStartPosition())-1;
    }

    private int endCol(JCTree tree){
        return (int)lineMap.getColumnNumber(tree.getEndPosition(endPosTable))-1;
    }

    private String line(int line){
        int lineStart = (int)lineMap.getStartPosition(line);
        int lineEnd = 0;
        try{
            lineEnd = (int)lineMap.getStartPosition(line+1);
        }catch (IndexOutOfBoundsException e){
            lineEnd = sourceCode.length();
        }

        return sourceCode.subSequence(lineStart,lineEnd).toString();

    }

    private int startLine(JCTree tree){
        return (int)lineMap.getLineNumber(tree.getStartPosition());
    }

    private int endLine(JCTree tree){
        return (int)lineMap.getLineNumber(tree.getEndPosition(endPosTable));
    }
}
