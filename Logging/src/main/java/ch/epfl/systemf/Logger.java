package ch.epfl.systemf;


//all methods return an integer, because otherwise we cannot call the in let bindings
//for now we assume that all the parent relationship can be express with the start parent scope
//and with the end parent scope. All event in the scope are children of the parent

public interface Logger{

    /*******************************************************
     **************** CONSTANTS ******************
     *******************************************************/

    /**************
     ********* Event Types
     **************/

    enum EventTypes {
        FLOW_ENTER("flowEnter"),
        FLOW_EXIT("flowExit"),
        STATEMENT_ENTER("statEnter"),
        STATEMENT_EXIT("statEXit"),
        EXPRESSION_ENTER("exprEnter"),
        EXPRESSION_EXIT("exprExit"),
        UPDATE("update");

        public final String name;

        EventTypes(String name) {
            this.name = name;
        }
    }
	/*******************************************************
	**************** API methods ******************
	*******************************************************/

	/**************
	********* Flow methods
	**************/

	public static int enterFlow(int nodeId){
		throw new UnsupportedOperationException();
	}

	public static int exitFlow(int nodeId){
		throw new UnsupportedOperationException();
	}

	/**************
	********* Statement methods
	**************/

	public static int enterStatement(int nodeId){
		throw new UnsupportedOperationException();
	}

	public static int exitStatement(int nodeId){
		throw new UnsupportedOperationException();
	}

	/**************
	********* Expression methods
	**************/

	public static int enterExpression(int nodeId){
		throw new UnsupportedOperationException();
	}

	public static int exitExpression(int nodeId, Object result){
		throw new UnsupportedOperationException();
	}

	/**************
	********* Update methods
	**************/

	public static int update(int nodeId, String varName, Object value){
		throw new UnsupportedOperationException();
	}
}
