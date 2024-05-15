export {
    EventId as EVENTID,
    NODEID,
    EVENT,
    STATMENT,
    EXPRESSION,
    FLOW,
    UPDATE,
    Value,
    NodeFormat,
    Identifier,
    Data,
    Write,
    LocalIdentifier,
    Literal,
    Label,
    LabelPos,
    Result,
    parseRawLabels,
    parseLabels
}

import { sourceCodeCache } from "./fetch"


/*******************************************************
 **************** EVENTS ******************
 *******************************************************/

type EVENT = FLOW | STATMENT | EXPRESSION | UPDATE | VoidCall | ResultCall

/**************
 ********* types
 **************/


// every event has an unique description and data schema
interface EventTemplate {
    type: 'EVENT',
    instanceInfo: InstanceInfo,
    description: EventType,
    idKey: string
}

// constraint idKey(event.description) === event.idKey
// don't know how to enforce the constraint currently on the type system
function idKey(t: EventType): string {
    return t.kind + "-" + t.subkind;
}

interface STATMENT extends EventTemplate {
    idKey: "statement-simple",
    result: Result | undefined
}


interface FLOW extends EventTemplate {
    idKey: "flow-simple",
}

interface EXPRESSION extends EventTemplate {
    idKey: "expression-simple",
    result: Result
}

interface UPDATE extends EventTemplate {
    idKey: "update-simple",
    write: Write
}

/********
**** call
********/

interface CallTemplate extends EventTemplate {
    owner: Reference | undefined,
    argsValues: ArgsValues | undefined
}

interface VoidCall extends CallTemplate {
    idKey: "statement-callVoid",
}

interface ResultCall extends CallTemplate {
    idKey: "statement-resultCall",
    result: Result | undefined
}

/********
**** constructor
********/

function makeEvent(label : RawLabel): EVENT {
    return {
        type: 'EVENT',
        instanceInfo: label.instanceInfo,
        description: label.eventDescription,
        idKey: idKey(label.eventDescription)
    } as any
}


/**************
 ********* instance info 
 **************/
type InstanceInfo = {
    eventId: EventId,
    nodeId: NODEID
}

type EventId = { id: number }

/********
**** parsing
********/

function instanceInfoFromJson(json: any[]): InstanceInfo {
    const eventId = { id: Number(readArrayField(1, json)) }
    const nodeId = readArrayField(2, json)

    return {
        eventId: eventId,
        nodeId: makeNodeId(nodeId)
    }

}


/**************
 ********* event types
 **************/
type EventType = { kind: string, subkind: string }

/********
**** parsing
********/

const eventTypes = {
    statement: { kind: "statement", subkind: "simple" },
    expression: { kind: "expression", subkind: "simple" },
    update: { kind: "update", subkind: "simple" },
    flow: { kind: "flow", subkind: "simple" },
    voidCall: { kind: "statement", subkind: "callVoid" },
    resultCall: { kind: "statement", subkind: "resultCall" }
}
const eventTypeKeys = Object.keys(eventTypes).map(k => idKey((eventTypes as any)[k]))

function eventTypeFromJson(json: any[]): EventType {
    const kind = readArrayField(3, json)
    const subkind = readArrayField(4, json)
    const t = { kind: kind, subkind: subkind }

    if (!eventTypeKeys.includes(idKey(t)))
        unableToParse(json)
    return t
}


/*******************************************************
 **************** Labels ******************
 *******************************************************/

/**************
 ********* Label pos
 **************/

enum DataType {
    StaticReference = "staticRef",
    InstanceRef = "instanceRef",
    LocalIdentifier = "localIdentifier",
    FieldIdentifier = "fieldIdentifier",
    Write = "write",
    ArgsValues = "argsValues",
    Result = "result"
}

enum LabelPos {
    START = "start",
    UPDATE = "update",
    END = "end",
    CALL = "call"
}

/********
**** parsing
********/

const PosValues = Object.keys(LabelPos).map(key => (LabelPos as any)[key])

function labelPosFromJson(json: any): LabelPos {
    const labelPos = json[0]
    if (PosValues.includes(labelPos)) {
        return labelPos
    } else {
        return unableToParse(json)
    }
}

/**************
 ********* Label
 **************/

type Label = {
    pos: LabelPos,
    event: EVENT
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
    const eventId = rawLabel.instanceInfo.eventId.id

    switch (rawLabel.pos) {
        case LabelPos.UPDATE:
            event = makeEvent(rawLabel)
            break;
        case LabelPos.START:
            event = makeEvent(rawLabel)
            partialEvents.set(eventId, event);
            break;
        case LabelPos.END:
            event = partialEvents.get(eventId) as EVENT
            partialEvents.delete(eventId);
            break;
        case LabelPos.CALL:
            event = partialEvents.get(eventId) as EVENT
    }

    schema.updateEventData(event, rawLabel)
    return {
        pos: rawLabel.pos,
        event: event
    }

}

/**************
 ********* schema
 **************/
interface Schema {
    updateEventData: (event: EVENT, label: RawLabel) => void,
    with: (eventSchema: EventSchema) => Schema
}

function emptySchema(): Schema {
    let labels: Map<string, LabelSchema> = new Map()
    let self: Schema = {
        updateEventData: (event: EVENT, label: RawLabel) => {
            const s = labels.get(labelKey(event.description, label.pos))
            if(s===undefined){
                unableToParse(label)
            }else{
                s.updateEventData(event, label)
            }
        }
    } as Schema
    self["with"] = (event: EventSchema) => {
        event.labels.map(l => labels.set(labelKey(event.description, l.pos), l))
        return self;
    }
    return self;
}

function labelKey(description: EventType, pos :LabelPos):string{
    return idKey(description)+"-"+pos;
}

interface EventSchema {
    description: EventType
    labels: LabelSchema[],
    with: (label: LabelSchema) => EventSchema
}

function eventSchema(description: EventType): EventSchema {
    let labels: LabelSchema[] = []
    let self: EventSchema = {
        description: description,
        labels : labels
    } as EventSchema
    self["with"] = (label: LabelSchema) => {
        labels.push(label)
        return self;
    }
    return self
}

interface LabelSchema {
    pos: LabelPos
    updateEventData: (event: EVENT, label: RawLabel) => void
}

function labelSchema(pos: LabelPos, data: [string, DataType[]][]): LabelSchema {
    return {
        pos: pos,
        updateEventData: (event: EVENT, label: RawLabel) => {
            const lData = label.data
            if (lData.length === 0)
                return;
            if (lData.length !== data.length)
                unableToParse(label.data)

            for (let i = 0; i < data.length; ++i) {
                const [field, dataTypes] = data[i]
                const d = lData[i] as any
                if (dataTypes.includes(d.dataType))
                    unableToParse(label.data)
                        (event as any)[field] = d
            }
        }
    }
}

/********
**** schema definition
********/
const refTypes = [DataType.InstanceRef, DataType.StaticReference]
const schema: Schema = emptySchema()
    .with(eventSchema(eventTypes.expression)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.END, [['result', [DataType.Result]]])))
    .with(eventSchema(eventTypes.flow)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.END, [])))
    .with(eventSchema(eventTypes.update)
        .with(labelSchema(LabelPos.UPDATE, [['write', [DataType.Write]]])))
    .with(eventSchema(eventTypes.voidCall)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.CALL, [['owner', refTypes], ['argsValues', [DataType.ArgsValues]]]))
        .with(labelSchema(LabelPos.END, [])))
    .with(eventSchema(eventTypes.resultCall)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.CALL, [['owner', refTypes], ['argsValues', [DataType.ArgsValues]]]))
        .with(labelSchema(LabelPos.END, [['result', [DataType.Result]]])))



/**************
 ********* raw label
 **************/

type RawLabel = {
    pos: LabelPos,
    instanceInfo: InstanceInfo,
    eventDescription: EventType,
    data: Data[]
}

/********
**** parsing
********/

function parseRawLabels(jsonTrace: any): RawLabel[] {
    return jsonTrace["trace"].map(parseRawLabel)
}

const startIndexData = 5

function parseRawLabel(json: any): RawLabel {

    return {
        pos: labelPosFromJson(json),
        instanceInfo: instanceInfoFromJson(json),
        eventDescription: eventTypeFromJson(json),
        data: json.slice(startIndexData, json.length).map(dataFromJson)
    }
}


/*******************************************************
 **************** Node format ******************
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

/*******************************************************
 **************** Label data ******************
 *******************************************************/
const typeField = "dataType"


/**************
 ********* Data
 **************/

type Data = Result | Write | ArgsValues | Identifier | Reference

/********
**** types
********/


type Result = {
    dataType: DataType.Result
    value: Value
}
type ArgsValues = {
    dataType: DataType.ArgsValues,
    values: Value[]
}
type Write = {
    dataType: DataType.Write,
    identifier: Identifier,
    value: Value
}

/********
**** parsing
********/

function dataFromJson(json: any): Data {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference: return staticRefFromJson(json)
        case DataType.InstanceRef: return instanceRefFromJson(json)
        case DataType.LocalIdentifier: return localIdentifierFromJson(json)
        case DataType.FieldIdentifier: return fieldIdentifierFromJson(json)
        case DataType.ArgsValues:
            return {
                dataType: DataType.ArgsValues,
                values: readJsonField("values", json)
                    .map(valueFromJson)
            }
        case DataType.Result:
            return {
                dataType: DataType.Result,
                value: valueFromJson(readJsonField("value", json))
            }
        case DataType.Write:
            return {
                dataType: DataType.Write,
                identifier: identifierFromJson(readJsonField("identifier", json)),
                value: valueFromJson(readJsonField("value", json))
            }
        default:
            return unableToParse(json)
    }
}

/**************
********* Value
**************/

type Value = Literal | InstanceReference;

/********
**** types
********/

type Literal = {
    type: 'Literal',
    kind: string,
    value: number | string | boolean
}


/********
**** parsing
********/
const valueField = "value"

function valueFromJson(json: any): Value {
    switch (readJsonField(typeField, json)) {
        case "null": return { type: 'Literal', kind: 'null', value: readJsonField(valueField, json) };
        case "int": return { type: 'Literal', kind: 'int', value: readJsonField(valueField, json) };
        case "long": return { type: 'Literal', kind: 'long', value: readJsonField(valueField, json) };
        case "bool": return { type: 'Literal', kind: 'bool', value: readJsonField(valueField, json) };
        case "string": return { type: 'Literal', kind: 'string', value: readJsonField(valueField, json) };
        case "char": return { type: 'Literal', kind: 'char', value: readJsonField(valueField, json) };
        case "byte": return { type: 'Literal', kind: 'byte', value: readJsonField(valueField, json) };
        case "short": return { type: 'Literal', kind: 'short', value: readJsonField(valueField, json) };
        case "float": return { type: 'Literal', kind: 'float', value: readJsonField(valueField, json) };
        case "double": return { type: 'Literal', kind: 'double', value: readJsonField(valueField, json) };
        case "instanceRef": return instanceRefFromJson(json);
        default: return unableToParse(json);
    }
}

/**************
********* Reference
**************/

type Reference = InstanceReference | StaticReference

/********
**** types
********/

type InstanceReference = {
    dataType: DataType.InstanceRef,
    clazz: ClassIdentifier,
    hashPointer: number,
    version: number
}

type StaticReference = {
    dataType: DataType.StaticReference,
    clazz: ClassIdentifier,
    version: number
}

type ClassIdentifier = {
    packageName: string,
    className: string
}

/********
**** parsing
********/
const versionField = "version"
const clazzField = "clazz"

function refFromJson(json: any): Reference {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference:
            return staticRefFromJson(json)
        case DataType.InstanceRef:
            return instanceRefFromJson(json)
        default:
            return unableToParse(json)
    }
}

function staticRefFromJson(json: any): StaticReference {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference:
            return {
                dataType: DataType.StaticReference,
                clazz: readJsonField(clazzField, json),
                version: readJsonField(versionField, json)
            }
        default:
            return unableToParse(json)
    }
}

function instanceRefFromJson(json: any): InstanceReference {
    switch (readJsonField(typeField, json)) {
        case DataType.InstanceRef:
            return {
                dataType: DataType.InstanceRef,
                clazz: readJsonField(clazzField, json),
                hashPointer: readJsonField("pointer", json),
                version: readJsonField(versionField, json)
            }
        default:
            return unableToParse(json)
    }
}

/**************
********* Identifier
**************/

type Identifier = LocalIdentifier | FieldIdentifier;

/********
**** types
********/

type LocalIdentifier = {
    parentNodeId: string,
    name: string
}


type FieldIdentifier = {
    owner: Reference,
    name: string
}

/********
**** parsing
********/

function identifierFromJson(json: any): Identifier {
    switch (readJsonField(typeField, json)) {
        case DataType.FieldIdentifier:
            return fieldIdentifierFromJson(json)
        case DataType.LocalIdentifier:
            return localIdentifierFromJson(json)
        default:
            return unableToParse(json)
    }
}

function fieldIdentifierFromJson(json: any): FieldIdentifier {
    switch (readJsonField(typeField, json)) {
        case DataType.FieldIdentifier:
            return {
                owner: refFromJson(readJsonField(json, "owner")),
                name: readJsonField(json, "name")
            }
        default:
            return unableToParse(json)
    }
}

function localIdentifierFromJson(json: any): LocalIdentifier {
    switch (readJsonField(typeField, json)) {
        case DataType.LocalIdentifier:
            return {
                parentNodeId: readJsonField(json, "parentNodeId"),
                name: readJsonField(json, "name")
            }

        default:
            return unableToParse(json)
    }
}

/*******************************************************
 **************** Json helpers ******************
 *******************************************************/

// when parsing all types throw exceptions if invalid format
// exceptions should not be catched
// a label data don't have undefined field but may not be present in the label

function unableToParse(json: any): any {
    throw new Error("unable to parse " + json);
}

function readArrayField(index: number, json: any): any {
    if (json.length > index) {
        return json[index]
    } else {
        unableToParse(json)
    }
}

function readJsonField(field: string, json: any): any {
    if (field in json) {
        return json[field]
    } else {
        unableToParse(json)
    }
}