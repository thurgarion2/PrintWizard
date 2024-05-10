export {
    EVENTID,
    NODEID,
    EVENT,
    STATMENT,
    EXPRESSION,
    FLOW,
    UPDATE,
    Value,
    NodeFormat,
    Identifier,
    EventKind,
    Field,
    Data,
    Write,
    FieldInObject,
    LocalIdentifier,
    Literal,
    ObjectValue,
    ObjectWithoutFields,
    Label,
    LabelPos,
    EventType,
    EventSubKind,
    Result,
    resultIsDefined,
    writeIsDefined,
    parseRawLabels,
    parseLabels
}

import { sourceCodeCache } from "./fetch"


/*******************************************************
 **************** EVENTS ******************
 *******************************************************/
interface EventTemplate {
    type: 'EVENT',
    description: EventDescription
    data: Data
}

type EVENT = FLOW | STATMENT | EXPRESSION | UPDATE | VoidCall | ResultCall

interface STATMENT extends EventTemplate {
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Statement, subkind: EventSubKind.Simple }
    }
    data: Result
}

interface VoidCall extends EventTemplate {
    type: 'EVENT',
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Statement, subkind: EventSubKind.VoidCall }
    }
    data: Empty
}

interface ResultCall extends EventTemplate {
    type: 'EVENT',
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }
    }
    data: Result
}

interface FLOW extends EventTemplate {
    type: 'EVENT',
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Flow, subkind: EventSubKind.Simple }
    }
    data: Empty
}

interface EXPRESSION extends EventTemplate {
    type: 'EVENT',
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Expression, subkind: EventSubKind.Simple }
    }
    data: Result
}

interface UPDATE extends EventTemplate {
    type: 'EVENT',
    description: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: { kind: EventKind.Update, subkind: EventSubKind.Simple }
    }
    data: Write
}

/**************
 ********* event description
 **************/

type EventDescription = {
    eventId: EVENTID,
    nodeId: NODEID,
    type: EventType
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

type EventType = { kind: EventKind, subkind: EventSubKind }

enum EventSubKind {
    Simple = "simple",
    VoidCall = "callVoid",
    ResultCall = "resultCall"
}

enum EventKind {
    Statement = "statement",
    Flow = "flow",
    Expression = "expression",
    Update = "update"
}

/**************
 ********* event data
 **************/
type Data = Empty | Result | Write
type Empty = { type: 'empty' }
type Result = { type: 'result', value: Literal | ObjectValue | undefined }
type Write = {
    type: 'write',
    varName: Field | LocalIdentifier | undefined,
    value: Literal | ObjectValue | undefined
}

/**************
 ********* makeEvent
 **************/

function makeEvent(description: EventDescription): EVENT {
    switch (keyType(description.type)) {
        case keyType({ kind: EventKind.Statement, subkind: EventSubKind.Simple }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Statement, subkind: EventSubKind.Simple }
                },
                data: makeResult()
            }
        case keyType({ kind: EventKind.Statement, subkind: EventSubKind.VoidCall }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Statement, subkind: EventSubKind.VoidCall }
                },
                data: makeEmpty()
            }

        case keyType({ kind: EventKind.Statement, subkind: EventSubKind.ResultCall }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }
                },
                data: makeResult()
            }
        case keyType({ kind: EventKind.Flow, subkind: EventSubKind.Simple }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Flow, subkind: EventSubKind.Simple }
                },
                data: makeEmpty()
            }
        case keyType({ kind: EventKind.Expression, subkind: EventSubKind.Simple }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Expression, subkind: EventSubKind.Simple }
                },
                data: makeResult()
            }
        case keyType({ kind: EventKind.Update, subkind: EventSubKind.Simple }):
            return {
                type: 'EVENT',
                description: {
                    eventId: description.eventId,
                    nodeId: description.nodeId,
                    type: { kind: EventKind.Update, subkind: EventSubKind.Simple }
                },
                data: makeWrite()
            }
        default:
            debugger;
            console.log("unknown event " + description)
            throw new Error()
    }
}

function keyType(type: EventType): any {
    return `${type.kind}-${type.subkind}`;
}

/**************
 ********* data operations
 **************/

function makeEmpty(): Empty {
    return { type: 'empty' }
}

function makeResult(): Result {
    return { type: 'result', value: undefined }
}

function resultIsDefined(res: Result): Boolean {
    return res.value !== undefined
}

function makeWrite(): Write {
    return { type: 'write', varName: undefined, value: undefined }
}

function writeIsDefined(write: Write): Boolean {
    return write.value !== undefined && write.varName !== undefined
}



/*******************************************************
 **************** Labels ******************
 *******************************************************/

/**************
 ********* types
 **************/

type RawLabel = {
    pos: LabelPos,
    eventDescription: {
        eventId: EVENTID,
        nodeId: NODEID,
        type: EventType
    },
    data: Map<string, any> | undefined
}

type Label = {
    pos: LabelPos,
    event: EVENT
}

enum LabelPos {
    START = "start",
    UPDATE = "update",
    END = "end",
    CALL = "call"
}

/**************
 ********* parse label
 **************/

function parseLabels(rawLabels: RawLabel[]): Label[] {
    let partialEvents: Map<number, EVENT> = new Map()
    return rawLabels.map(l => parseLabel(l, partialEvents))
}

function parseLabel(rawLabel: RawLabel, partialEvents: Map<number, EVENT>): Label {
    let event: EVENT

    switch (rawLabel.pos) {
        case LabelPos.UPDATE:
            event = makeEvent(rawLabel.eventDescription)
            break;
        case LabelPos.START:
            event = makeEvent(rawLabel.eventDescription)
            partialEvents.set(rawLabel.eventDescription.eventId.id, event);
            break;
        case LabelPos.END:
            event = partialEvents.get(rawLabel.eventDescription.eventId.id) as EVENT
            partialEvents.delete(rawLabel.eventDescription.eventId.id);
            break;
        case LabelPos.CALL:
            event = partialEvents.get(rawLabel.eventDescription.eventId.id) as EVENT
    }

    const fields = rawLabel.data === undefined ? [] : rawLabel.data.entries()
    for (const [name, value] of fields) {
        ((event.data) as any)[name] = value
    }

    console.assert(event !== undefined)

    return {
        pos: rawLabel.pos,
        event: event
    }

}

/**************
 ********* parse raw labels
 **************/


function parseRawLabels(jsonTrace: any): RawLabel[] {
    return jsonTrace["trace"].map(parseRawLabel)
}

function parseRawLabel(jsonLabel: any[]): RawLabel {
    const pos: LabelPos = parseEnum(jsonLabel[0], LabelPos)

    const eventId: EVENTID = {
        type: 'EVENTID',
        id: Number(jsonLabel[1])
    }

    const nodeId: NODEID = makeNodeId(jsonLabel[2])
    const type: EventType = {
        kind: parseEnum(jsonLabel[3], EventKind),
        subkind: parseEnum(jsonLabel[4], EventSubKind)
    }


    let labelSchema = schema([pos, type])
    labelSchema = labelSchema

    const rawData = jsonLabel.slice(5, jsonLabel.length);
    let data = undefined

    if (rawData.length > 0) {
        console.assert(labelSchema.length === rawData.length, `schema for [${pos},${type}] is not correct`)
        data = new Map<string, any>();

        for (let i = 0; i < rawData.length; ++i) {

            data.set(labelSchema[i].name, labelSchema[i].parse(rawData[i]))
        }
    }

    return {
        pos: pos,
        eventDescription: {
            eventId: eventId,
            nodeId: nodeId,
            type: type
        },
        data: data
    }

}

/********
 **** schema
 ********/

function schema(key: [LabelPos, EventType]): { name: string, parse: (json: any) => any }[] {
    const sKey = stringKey(key)
    if (schemaMapping.has(sKey)) {
        return schemaMapping.get(sKey) as { name: string, parse: (json: any) => any }[]
    }

    debugger;
    console.log(`we don't have a schema for ${key}`)
    throw new Error()

}

function stringKey(key: [LabelPos, EventType]): string {
    return `${key[0]}-${key[1].kind}-${key[1].subkind}`
}


const schemaFields: [[LabelPos, EventType], { name: string, parse: (json: any) => any }[]][] = [
    [[LabelPos.START, { kind: EventKind.Statement, subkind: EventSubKind.Simple }], []],
    [[LabelPos.END, { kind: EventKind.Statement, subkind: EventSubKind.Simple }],
    [{ name: 'value', parse: valueFromJson }]],
    [[LabelPos.START, { kind: EventKind.Statement, subkind: EventSubKind.VoidCall }], []],
    [[LabelPos.CALL, { kind: EventKind.Statement, subkind: EventSubKind.VoidCall }],
    [{ name: 'args', parse: (arr: any[]) => arr.map(valueFromJson) }]],
    [[LabelPos.END, { kind: EventKind.Statement, subkind: EventSubKind.VoidCall }], []],
    [[LabelPos.START, { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }], []],
    [[LabelPos.CALL, { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }],
    [{ name: 'args', parse: (arr: any[]) => arr.map(valueFromJson) }]],
    [[LabelPos.END, { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }],
    [{ name: 'value', parse: valueFromJson }]],
    [[LabelPos.START, { kind: EventKind.Flow, subkind: EventSubKind.Simple }], []],
    [[LabelPos.END, { kind: EventKind.Flow, subkind: EventSubKind.Simple }], []],
    [[LabelPos.START, { kind: EventKind.Expression, subkind: EventSubKind.Simple }], []],
    [[LabelPos.END, { kind: EventKind.Expression, subkind: EventSubKind.Simple }],
    [{ name: 'value', parse: valueFromJson }]],
    [[LabelPos.UPDATE, { kind: EventKind.Update, subkind: EventSubKind.Simple }], []],
    [[LabelPos.UPDATE, { kind: EventKind.Update, subkind: EventSubKind.Simple }],
    [{ name: 'varName', parse: identifierFromJson }, { name: 'value', parse: valueFromJson }]]
];

const schemaMapping: Map<string, { name: string, parse: (json: any) => any }[]> = new Map(schemaFields.map(([k, value]) => [stringKey(k), value]));


/********
 **** parse enum
 ********/

function parseEnum<Enum>(x: any, enum_: any): Enum {
    const values = Object.keys(enum_).map(k => (enum_ as any)[k])

    if (!values.includes(x)) {
        debugger;
        console.log("unknown value " + x)
        throw new Error()
    } else {
        return x as Enum;
    }
}

/*******************************************************
 **************** Value representation ******************
 *******************************************************/


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
    obj: ObjectValue
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

type Value = Literal | ObjectValue | ObjectWithoutFields;

type NoResult = {
    type: 'NoResult'
}

type Literal = {
    type: 'Literal',
    kind: string,
    value: number | string | boolean
}

type ObjectValue = {
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
        case "bool":
            return {
                type: 'Literal',
                kind: 'bool',
                value: Boolean(value['value'])
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
            return {
                type: 'LocalIdentifier',
                name: value
            };
            debugger;
            throw new Error(value);

    }
}