export { 
   eventId,
   nodeId,
   kind,
   parseEvent,
   assignValue,
   varName,
   value,
   isControlFlow,
   result
}

export const eventKinds = {
    statementEnter : 0,
    statementExit : 1,
    flowEnter : 2,
    flowExit : 3,
    expressionEnter : 4,
    expressionExit : 5,
    update : 6
}

const eventId = function(event){
    return event.eventId;
}

const nodeId = function(event){
    return event.nodeId;
}

const kind = function(event){
    return event.kind;
}

const isControlFlow = function(kind){

    return kind==eventKinds.flowEnter || kind==eventKinds.flowExit;
}

const result = function(event){
    switch(event.kind){
        case eventKinds.expressionExit:
            return event.result;
        default:
            console.assert(false);
            return undefined;    
    }
}

//return a record with varName and value (for varName=value)
const assignValue = function(event){
    if(kind(event)!=eventKinds.update){
        console.log(event);
        console.assert(false);
        return undefined;
    }
    return event;
}

const varName = function(assign){
    return assign.varName;
}

const value = function(assign){
    return assign.value;
}

const parseEvent = function(line){
    const fields = line.split(', ');
    console.assert(fields.length>=3);

    const eventId = fields[0];
    const kind = fields[1];
    const nodeId = fields[2];
    const data = fields.slice(3,fields.length);

    const params = {eventId:eventId, nodeId: nodeId, data:data};

    switch(kind){
        case 'statEnter':
            return makeStatEnter(params);
        case 'statExit':
            return makeStatExit(params);
        case 'flowEnter':
            return makeFlowEnter(params);
        case 'flowExit':
            return makeFlowExit(params);
        case 'update':
            return makeUpdate(params);
        case 'exprEnter':
            return makeExprEnter(params);
        case 'exprExit':   
            return makeExprExit(params); 
        default:
            console.log(kind);
            console.assert(false);
            return undefined;
    }
}

const makeExprEnter = function(params){
    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.expressionEnter,
    };
}

const makeExprExit = function(params){
    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.expressionExit,
        result: params.data[0]
    };
}

const makeUpdate = function(params){
    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.update,
        varName : params.data[0],
        value : params.data[1]
    };
}

const makeFlowEnter = function(params){

    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.flowEnter
    };
}

const makeFlowExit = function(params){

    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.flowExit
    };
}


const makeStatEnter = function(params){

    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.statementEnter
    };
}

const makeStatExit = function(params){

    return {
        eventId : params.eventId,
        nodeId : params.nodeId,
        kind : eventKinds.statementExit
    };
}