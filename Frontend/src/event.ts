export {
   EVENTID,
   NODEID,
   EVENT,
   STATMENT,
   EXPRESSION,
   FLOW,
   UPDATE,
   ASSIGN,
   EventKinds,
   assign,
   result,
   eventIdRepr,
   nodeIdRepr,
   parseEvent,
   labelEventId,
   isSingleLabel
}


/*******************************************************
 **************** EVENTS ******************
 *******************************************************/


/**************
 ********* Types
 **************/


type EVENTID = {
    type : 'EVENTID',
    id : number
}

type ASSIGN = {varName : string, value : string}

type NODEID = {
    type : "NODEID",
    id : string
}

type EVENT = {
    type : 'EVENT',
    eventId : EVENTID,
    nodeId : NODEID,
    kind : EventKinds
}

enum EventKinds {
    Statement,
    Flow,
    Expression,
    Update
}

type STATMENT = {
    type : 'EVENT',
    kind : EventKinds.Statement,
    eventId : EVENTID,
    nodeId : NODEID
}

type FLOW = {
    type : 'EVENT',
    kind : EventKinds.Flow,
    eventId : EVENTID,
    nodeId : NODEID
}

type EXPRESSION = {
    type : 'EVENT',
    kind : EventKinds.Expression,
    eventId : EVENTID,
    nodeId : NODEID,
    result : string
}

type UPDATE = {
    type : 'EVENT',
    kind : EventKinds.Update,
    eventId : EVENTID,
    nodeId : NODEID,
    varName : string,
    value : string
}

/**************
 ********* Type constructors
 **************/


/********
 **** Implementation type
 ********/


type PARAMS = {
    eventId : EVENTID, 
    nodeId : NODEID,
    data : string[]
}

/********
 **** constructors
 ********/


const makeEventId = function(id : number):EVENTID  {
    return {
        type : 'EVENTID',
        id : id
    };
}


const makeNodeId = function(id : string):NODEID  {
    return {
        type : 'NODEID',
        id : id
    };
}

const makeStatment = function(params : PARAMS) : STATMENT {
    return {
        type : 'EVENT',
        kind : EventKinds.Statement,
        eventId : params.eventId,
        nodeId : params.nodeId
    };
}


const makeFlow = function(params : PARAMS) : FLOW {
    return {
        type : 'EVENT',
        kind : EventKinds.Flow,
        eventId : params.eventId,
        nodeId : params.nodeId
    };
}


const makeExpr = function(params : PARAMS) : EXPRESSION {
    return {
        type : 'EVENT',
        kind : EventKinds.Expression,
        eventId : params.eventId,
        nodeId : params.nodeId,
        result : params.data[0]
    };
}

const makeUpdate = function(params : PARAMS) : UPDATE{
    return {
        type : 'EVENT',
        kind : EventKinds.Update,
        eventId : params.eventId,
        nodeId : params.nodeId,
        varName : params.data[0],
        value : params.data[1]
    };
}

/**************
 ********* types api
 **************/

/********
 **** EVENTID api
 ********/

const eventIdRepr = function(id : EVENTID):string {
    return id.id.toString();
}

/********
 **** NODEID api
 ********/

const nodeIdRepr = function(id : NODEID):string {
    return id.id;
}

/********
 **** UPDATE api
 ********/

const assign = function(event : UPDATE): ASSIGN{
    return {varName : event.varName, value : event.value};
}

/********
 **** EXPRESSION api
 ********/

const result = function(event : EXPRESSION):string{
    return event.result;
}


/**************
 ********* parse event from csv representation
 **************/

/********
 **** parse event
 ********/

const parseEvent = function(startLabel : string[], endLabel : string[]):EVENT{

    console.assert(startLabel.length>=3 && endLabel.length>=3);
   
    console.assert(startLabel[0]===endLabel[0] && startLabel[2]===endLabel[2]);

    const eventId = makeEventId(labelEventId(startLabel));
    const kind = startLabel[1];
    const nodeId = makeNodeId(startLabel[2]);


    const data = endLabel.slice(3);

    const params : PARAMS = {eventId:eventId, nodeId: nodeId, data:data};

    switch(kind){
        case 'statEnter':
            return makeStatment(params);
        case 'flowEnter':
            return makeFlow(params);
        case 'update':
            return makeUpdate(params);
        case 'exprEnter':
            return makeExpr(params);
        default:
            throw new Error('unknown kind '+kind);
    }
}


/********
 **** helpers
 ********/


const labelEventId = function(label : string[]): number{
    if(label==undefined)
        debugger;
    return Number(label[0]);
}

//for now this helper is needed when parsing the csv, but we should find a way
//to not show it to the outside world
const isSingleLabel = function(label : string[]): boolean{
    switch(label[1]){
        case 'update':
            return true;
        default:
            return false;    
    }
}