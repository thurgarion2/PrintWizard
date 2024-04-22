package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.CompilationUnitTree;
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

public class NodeDataJson implements NodeData{

    private record EvaluationData(String line, int startCol, int endCol){};

    private static final String fileName = "source_format.json";
    private final JSONObject nodesData = new JSONObject();
    private final LineMap lineMap;
    private final EndPosTable endPosTable;
    private final CharSequence source;

    public NodeDataJson(JCTree.JCCompilationUnit compilationUnit){
        this.lineMap = compilationUnit.getLineMap();
        this.endPosTable = compilationUnit.endPositions;
        if(compilationUnit.getSourceFile() instanceof PathFileObject path){
            try {
                source = path.getCharContent(false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void dataForLoop(NodeId forLoop, NodeId init, NodeId iter, NodeId cond, JCTree.JCForLoop loop) {
        addData(cond, nodeDescription(loop.cond));
    }

    @Override
    public void dataWhileLoop(NodeId whileLoop, NodeId iter, NodeId cond, JCTree.JCWhileLoop loop) {
        addData(cond, nodeDescription(loop.cond));
    }

    @Override
    public void dataIf(NodeId iF, NodeId cond, NodeId theN, NodeId elsE, JCTree.JCIf branch) {
        addData(cond, nodeDescription(branch.cond));
    }

    @Override
    public void dataUnary(NodeId nodeId, JCTree.JCUnary unary) {
        addData(nodeId, nodeDescription(unary));
    }

    @Override
    public void dataVarDecl(NodeId nodeId, JCTree.JCVariableDecl varDecl) {
        addData(nodeId, nodeDescription(varDecl));
    }

    @Override
    public void dataCall(NodeId nodeId, JCTree.JCMethodInvocation call) {
        addData(nodeId, nodeDescription(call));
    }

    @Override
    public void dataReturn(NodeId nodeId, JCTree.JCReturn ret) {
        addData(nodeId, nodeDescription(ret));
    }

    @Override
    public void end() {
        try( Writer writer = new FileWriter(fileName);
                BufferedWriter buffered = new BufferedWriter(writer)) {
            nodesData.write(buffered);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addData(NodeId id, EvaluationData data){
        JSONObject json = new JSONObject();
        json.put("line", data.line());
        json.put("startCol", data.startCol);
        json.put("endCol", data.endCol);
        nodesData.put(Integer.toString(id.nodeId()), json);
    }


    private EvaluationData nodeDescription(JCTree tree){
        int startLine = startLine(tree);
        int endLine = endLine(tree);

        if(startLine!=endLine)
            throw new UnsupportedOperationException();
        return new EvaluationData(
                line(startLine),
                startCol(tree),
                endCol(tree));
    }

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
            lineEnd = source.length();
        }

        return source.subSequence(lineStart,lineEnd).toString();

    }

    private int startLine(JCTree tree){
        return (int)lineMap.getLineNumber(tree.getStartPosition());
    }

    private int endLine(JCTree tree){
        return (int)lineMap.getLineNumber(tree.getEndPosition(endPosTable));
    }
}
