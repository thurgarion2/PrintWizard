package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;


public class SampleJavacPlugin implements Plugin {

    @Override
    public String getName() {
        return "MyPlugin";
    }

    public static String display(CompilationUnitTree t){
        return t==null ? "Null" : t.getClass().toString();
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context ctx  = ((BasicJavacTask) task).getContext();
        System.out.println("hello world");
        Names names = Names.instance(ctx);
        TreeMaker mkTree = TreeMaker.instance(ctx);
        Symtab symb = Symtab.instance(ctx);
        Types types = Types.instance(ctx);

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if(e.getKind()== TaskEvent.Kind.ANALYZE){
                    TreeHelper instr = new TreeHelper(mkTree,names, symb);
                    Logger logHelper = new Logger(instr);
                    SourceFormat makeNodeId = new SourceFormat((JCTree.JCCompilationUnit)e.getCompilationUnit());
                    TreeInstrumenter t = new TreeInstrumenter(logHelper, instr, types, makeNodeId);
                    t.translate((JCTree.JCCompilationUnit)e.getCompilationUnit());
                }

            }
        });
    }

}