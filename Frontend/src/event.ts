export {
    EVENTID,
    NODEID,
    EVENT,
    STATMENT,
    EXPRESSION,
    FLOW,
    UPDATE,
    ASSIGN,
    Value,
    NodeFormat,
    Identifier,
    EventKind as EventKinds,
    Field,
    FieldInObject,
    LocalIdentifier,
    Literal,
    Object,
    ObjectWithoutFields,
    assign,
    result,
    eventIdRepr,
    parseEvent,
    labelEventId,
    isSingleLabel
}

import { sourceCodeCache } from "./fetch"


/*******************************************************
 **************** EVENTS ******************
 *******************************************************/

type EVENT = FLOW | STATMENT | EXPRESSION | UPDATE | VoidSTATMENT

type STATMENT = {
    type: 'EVENT',
    eventType: EventType.Statement,
    kind: EventKind.Simple,
    nodeId: NODEID,
    eventId: EVENTID,
    data: Result
}

type VoidSTATMENT = {
    type: 'EVENT',
    eventType: EventType.Statement,
    kind: EventKind.Void,
    nodeId: NODEID,
    eventId: EVENTID,
    data: Empty
}

type FLOW = {
    type: 'EVENT',
    eventType: EventType.Flow,
    kind: EventKind.Simple,
    nodeId: NODEID,
    eventId: EVENTID,
    data: Empty
}

type EXPRESSION = {
    type: 'EVENT',
    eventType: EventType.Expression,
    kind: EventKind.Simple,
    nodeId: NODEID,
    eventId: EVENTID,
    data: Result
}

type UPDATE = {
    type: 'EVENT',
    eventType: EventType.Update,
    kind: EventKind.Simple,
    nodeId: NODEID,
    eventId: EVENTID,
    data: Write
}


/**************
 ********* event id
 **************/

type EVENTID = {
    type: 'EVENTID',
    id: number
}


/**************
 ********* event types/kind
 **************/


enum EventKind {
    Simple,
    Void
}

enum EventType {
    Statement,
    Flow,
    Expression,
    Update
}

/**************
 ********* event data
 **************/

type Empty = {}
type Result = { value: Literal | Object }
type Write = {
    varName: Field | LocalIdentifier,
    value: Literal | Object
}



/*******************************************************
 **************** Value representation ******************
 *******************************************************/


type ASSIGN = {
    varName: Field | LocalIdentifier,
    value: Literal | Object
}

type NODEID = NodeFormat | UnknownFormat

type NodeFormat = {
    type: "NodeFormat",
    lineNumber: number,
    line: string,
    startCol: number,
    endCol: number
}

type UnknownFormat = {
    type: "UnknownFormat",
    line: string
}


type Identifier = Field | FieldInObject | LocalIdentifier;

type Field = {
    type: 'Field',
    name: string,
    classDef: string,
    obj: Object
}

type FieldInObject = {
    type: 'FiledInObject',
    name: string,
    classDef: string,
}

type LocalIdentifier = {
    type: 'LocalIdentifier',
    name: string
}

type Value = Literal | Object | ObjectWithoutFields;

type NoResult = {
    type: 'NoResult'
}

type Literal = {
    type: 'Literal',
    kind: string,
    value: number | string | boolean
}

type Object = {
    type: 'Object',
    class: string,
    id: number,
    fields: { "identifier": FieldInObject, "value": Literal | ObjectWithoutFields }[]
}

type ObjectWithoutFields = {
    type: 'ObjectWithoutFields',
    class: string,
    id: number,
}

/**************
 ********* Type constructors
 **************/


/********
 **** Implementation type
 ********/


type PARAMS = {
    eventId: EVENTID,
    nodeId: NODEID,
    data: any[]
}

/********
 **** constructors
 ********/


const makeEventId = function (id: number): EVENTID {
    return {
        type: 'EVENTID',
        id: id
    };
}


const makeNodeId = function (id: string): NODEID {
    const result = sourceCodeCache().data()

    switch (result.type) {
        case 'failure':
            return {
                type: "UnknownFormat",
                line: id
            };
        case 'success':
            const format = result.payload
            if (id in format) {
                const nodeInfo: any = format[id]
                return {
                    type: "NodeFormat",
                    lineNumber: nodeInfo['lineNumber'],
                    line: nodeInfo['line'],
                    startCol: nodeInfo['startCol'],
                    endCol: nodeInfo['endCol']
                }
            } else {
                return {
                    type: "UnknownFormat",
                    line: id
                };
            }
    }

}

const makeNoResult = function (): NoResult {
    return {
        type: 'NoResult'
    };
}

const makeStatment = function (params: PARAMS): STATMENT {
    return {
        type: 'EVENT',
        kind: EventKind.Statement,
        eventId: params.eventId,
        nodeId: params.nodeId,
        result: makeNoResult()
    };
}


const makeFlow = function (params: PARAMS): FLOW {
    return {
        type: 'EVENT',
        kind: EventKind.Flow,
        eventId: params.eventId,
        nodeId: params.nodeId
    };
}


const makeExpr = function (params: PARAMS): EXPRESSION {
    return {
        type: 'EVENT',
        kind: EventKind.Expression,
        eventId: params.eventId,
        nodeId: params.nodeId,
        result: params.data[0]
    };
}

const makeUpdate = function (params: PARAMS): UPDATE {
    const varName: any = identifierFromJson(params.data[0])
    const value: any = valueFromJson(params.data[1])
    return {
        type: 'EVENT',
        kind: EventKind.Update,
        eventId: params.eventId,
        nodeId: params.nodeId,
        varName: varName,
        value: value
    };
}

/********
 **** value from Json
 ********/

function valueFromJson(value: any): Value {
    const kind: string = value["type"]
    switch (kind) {
        case "int":
            return {
                type: 'Literal',
                kind: 'int',
                value: Number(value['value'])
            }
        case "null":
            return {
                type: 'Literal',
                kind: 'null',
                value: 'null'
            }
        case "object":
            return {
                type: 'Object',
                class: value["class"],
                id: value["id"],
                fields: value['fields'].map((f: any) => {
                    return {
                        "identifier": identifierFromJson(f["identifier"]),
                        "value": valueFromJson(f["value"])
                    };
                })
            }
        case "objectWithout":
            return {
                type: "ObjectWithoutFields",
                class: value["class"],
                id: value["id"]
            }
        default:
            console.log(value)
            throw new Error(value);

    }
}

/********
 **** identifier formJson
 ********/

function identifierFromJson(value: any): Identifier {
    const kind: string = value["type"]

    switch (kind) {
        case "local":
            return {
                type: 'LocalIdentifier',
                name: value["name"]
            };
        case "field":
            const obj: any = valueFromJson(value["obj"])
            return {
                type: 'Field',
                name: value["name"],
                classDef: value["classDef"],
                obj: obj
            }
        case "fieldIn":
            return {
                type: 'FiledInObject',
                name: value["name"],
                classDef: value["classDef"]
            }
        default:
            debugger;
            throw new Error(value);

    }
}

/**************
 ********* types api
 **************/


/********
 **** EVENTID api
 ********/

const eventIdRepr = function (id: EVENTID): string {
    return id.id.toString();
}


/********
 **** UPDATE api
 ********/

const assign = function (event: UPDATE): ASSIGN {
    return { varName: event.varName, value: event.value };
}

/********
 **** EXPRESSION api
 ********/

const result = function (event: EXPRESSION): Object | Literal {
    return event.result;
}


/**************
 ********* parse event from array representation
 **************/

/********
 **** parse event
 ********/

const parseEvent = function (startLabel: any[], endLabel: any[]): EVENT {

    console.assert(startLabel.length >= 3 && endLabel.length >= 3);

    console.assert(startLabel[0] === endLabel[0] && startLabel[2] === endLabel[2]);

    const eventId = makeEventId(labelEventId(startLabel));
    const kind: string = startLabel[1];
    const nodeId = makeNodeId(startLabel[2]);


    const data = endLabel.slice(3);

    const params: PARAMS = { eventId: eventId, nodeId: nodeId, data: data };

    switch (kind) {
        case 'statEnter':
            return makeStatment(params);
        case 'flowEnter':
            return makeFlow(params);
        case 'update':
            return makeUpdate(params);
        case 'exprEnter':
            return makeExpr(params);
        default:
            throw new Error('unknown kind ' + kind);
    }
}


/********
 **** helpers
 ********/


const labelEventId = function (label: any[]): number {
    if (label == undefined)
        debugger;
    return Number(label[0]);
}

//for now this helper is needed when parsing the csv, but we should find a way
//to not show it to the outside world
const isSingleLabel = function (label: any[]): boolean {
    switch (label[1]) {
        case 'update':
            return true;
        default:
            return false;
    }
}