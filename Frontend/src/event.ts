export {
    EventId as EVENTID,
    EventType,
    NodeSourceFormat,
    EVENT,
    STATMENT,
    EXPRESSION,
    FLOW,
    UPDATE,
    Value,
    SourceFormatDescription,
    PresentInSourceCode,
    Token,
    parseSourceFormatDescription,
    Identifier,
    Data,
    Write,
    LocalIdentifier,
    Literal,
    FieldIdentifier,
    Label,
    LabelPos,
    Result,
    EventKind,
    DataType,
    ArgsValues,
    EventSubKind,
    InstanceReference,
    parseRawLabels,
    parseLabels,
    valueFromJson,
    findNodeSynthax
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

// the types below are help to programming but you should check the schema below to have 
// that define the true information
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
    result: Result | undefined
}

interface UPDATE extends EventTemplate {
    idKey: "update-simple",
    write: Write | undefined
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

function makeEvent(label: RawLabel): EVENT {
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
    nodeId: NodeSourceFormat
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
        nodeId: findNodeSynthax(nodeId)
    }

}


/**************
 ********* event types
 **************/
type EventType = { kind: string, subkind: string }

/********
**** parsing
********/
enum EventKind {
    Statement = "statement",
    Expression = "expression",
    Flow = "flow",
    Update = "update"
}

enum EventSubKind {
    Simple = "simple",
    CallVoid = "callVoid",
    ResultCall = "resultCall"
}

const eventTypes = {
    statement: { kind: EventKind.Statement, subkind: EventSubKind.Simple },
    expression: { kind: EventKind.Expression, subkind: EventSubKind.Simple },
    update: { kind: EventKind.Update, subkind: EventSubKind.Simple },
    flow: { kind: EventKind.Flow, subkind: EventSubKind.Simple },
    voidCall: { kind: EventKind.Statement, subkind: EventSubKind.CallVoid },
    resultCall: { kind: EventKind.Statement, subkind: EventSubKind.ResultCall }
}
const eventTypeKeys = Object.keys(eventTypes).map(k => idKey((eventTypes as any)[k]))


function eventTypeFromJson(json: any[]): EventType {
    const kind = readArrayField(4, json)
    const subkind = readArrayField(3, json)
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
    Result = "result",
    Literal = "literal"
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
            if (s === undefined) {
                unableToParse(label)
            } else {
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

function labelKey(description: EventType, pos: LabelPos): string {
    return idKey(description) + "-" + pos;
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
        labels: labels
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
                if (!dataTypes.includes(d.dataType)) {
                    unableToParse(label.data)
                }
                (event as any)[field] = d
            }
        }
    }
}

/********
**** schema definition
********/
const refTypes = [DataType.InstanceRef, DataType.StaticReference]
const resultField = 'result'

const schema: Schema = emptySchema()
    .with(eventSchema(eventTypes.expression)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.END, [[resultField, [DataType.Result]]])))
    .with(eventSchema(eventTypes.flow)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.END, [])))
    .with(eventSchema(eventTypes.statement)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.END, [[resultField, [DataType.Result]]])))
    .with(eventSchema(eventTypes.update)
        .with(labelSchema(LabelPos.UPDATE, [['write', [DataType.Write]]])))
    .with(eventSchema(eventTypes.voidCall)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.CALL, [['owner', refTypes], ['argsValues', [DataType.ArgsValues]]]))
        .with(labelSchema(LabelPos.END, [])))
    .with(eventSchema(eventTypes.resultCall)
        .with(labelSchema(LabelPos.START, []))
        .with(labelSchema(LabelPos.CALL, [['owner', refTypes], ['argsValues', [DataType.ArgsValues]]]))
        .with(labelSchema(LabelPos.END, [[resultField, [DataType.Result]]])))



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



type NodeSourceFormat = PresentInSourceCode | AbsentFromSourceCode

/**************
 ********* types
 **************/

type SourceFormatDescription = {
    sourceFile: SourceFile,
    syntaxNodes: any
}

/********
**** SourceFile definition
********/

type SourceFile = {
    packageName: string,
    fileName: string
}

/********
**** Present In Source Code definition
********/

type PresentInSourceCode = {
    tokens: Token[],
    children: string[],
    sourceFile: SourceFile,
    identifier: string,
    startLine: number,
    endLine: number,
    expression: Expression,
    prefix: Text,
    suffix: Text,
    kind: "presentInSourceCode"
}

/********
**** Token definition
********/

type Token = Text | Child | LineStart

type Text = {
    kind: "Text",
    text: string
}

type Expression = {
    kind: "expression",
    tokens: Token[]
}


type Child = {
    kind: "Child",
    childIndex: number,
    text: string
}

type LineStart = {
    kind: "LineStart",
    lineNumber: number
}

/********
**** Absent From SourceCode definition
********/


type AbsentFromSourceCode = {
    kind: "absent",
    identifier: string
}

/**************
 ********* schema
 **************/

/********
**** schema defintions
********/

// we validate that the types have the right form


const SourceFileSchema = jsonSchemaBuilder()
    .stringField("packageName")
    .stringField("fileName")

const TextSchema = jsonSchemaBuilder()
    .constField("kind", "Text")
    .stringField("text")


const ChildSchema = jsonSchemaBuilder()
    .constField("kind", "Child")
    .numberField("childIndex")
    .stringField("text")

const LineStartSchema = jsonSchemaBuilder()
    .constField("kind", "LineStart")

const AbsentFromSourceCodeSchema = jsonSchemaBuilder()
    .constField("kind", "absent")
    .stringField("identifier")

const TokenSchema = sumJsonSchema([TextSchema, ChildSchema, LineStartSchema])

const ExpressionSchema = jsonSchemaBuilder()
    .constField("kind", "expression")
    .array("tokens", TokenSchema)


const PresentInSourceCodeSchema = jsonSchemaBuilder()
    .array("tokens", TokenSchema)
    .array("children", { valid: (s: any) => typeof s === 'string' })
    .objectField("sourceFile", SourceFileSchema)
    .objectField("expression", ExpressionSchema)
    .objectField("prefix", TextSchema)
    .objectField("suffix", TextSchema)
    .stringField("identifier")
    .constField("kind", "presentInSourceCode")

const NodeSourceFormatSchema = sumJsonSchema([PresentInSourceCodeSchema, AbsentFromSourceCodeSchema])

const SourceFormatDescriptionSchema = jsonSchemaBuilder()
    .objectField("sourceFile", SourceFileSchema)
    .objectField("syntaxNodes", {
        valid: (m: any) => {
            return Object.values(m).every(v => NodeSourceFormatSchema.valid(v))
        }
    }

    )


/********
****  Json schema
********/

interface JsonSchema {
    valid: (json: any) => boolean
}

interface JsonSchemaBuilder {
    numberField: (field: string) => JsonSchemaBuilder,
    constField: (field: string, const_: string) => JsonSchemaBuilder,
    array: (field: string, schema: JsonSchema) => JsonSchemaBuilder,
    stringField: (field: string) => JsonSchemaBuilder,
    objectField: (field: string, schema: JsonSchema) => JsonSchemaBuilder,
    valid: (json: any) => boolean

}
//all sum type have a kind field we could use that to dispatch it efficently
function sumJsonSchema(schemas: JsonSchema[]): JsonSchema {
    return {
        valid: (json: any) => schemas.some(s => s.valid(json))
    }
}


//not immutable
function jsonSchemaBuilder(): JsonSchemaBuilder {

    function helper(fields: Map<string, (value: any) => boolean>): JsonSchemaBuilder {
        return {
            numberField: (field: string) => helper(fields.set(field, (value: any) => typeof value === "number")),
            stringField: (field: string) => helper(fields.set(field, (value: any) => typeof value === "string")),
            objectField: (field: string, schema: JsonSchema) => helper(fields.set(field, (value: any) => schema.valid(value))),
            constField: (field: string, const_: string) => helper(fields.set(field, (value: any) => value === const_)),
            array: (field: string, schema: JsonSchema) => helper(fields.set(field, (value: any) => value.every((v: any) => schema.valid(v)))),
            valid: (json: any) => {
                return Object.keys(json).every(field =>
                    fields.has(field) && (fields.get(field) as any)(json[field]))
            }
        }
    }

    return helper(new Map())
}

/********
****  extract Node Source Format
********/

function findNodeSynthax(nodeId: any): NodeSourceFormat {
    if (typeof (nodeId) !== 'string')
        throw new Error('nodeId should be a string')

    const data = sourceCodeCache().data()
    switch (data.type) {
        case 'failure': return {
            kind: "absent",
            identifier: nodeId
        }
        case 'success':
            const nodes = data.payload.syntaxNodes

            if (nodeId in nodes) {
                return nodes[nodeId] as any
            } else {
                return {
                    kind: "absent",
                    identifier: nodeId
                }
            }
    }

}


function parseSourceFormatDescription(json: any): SourceFormatDescription {
    if (!SourceFormatDescriptionSchema.valid(json))
        console.log("invalide source code format file")
    return json;
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
                value: valueFromJson(readJsonField("result", json))
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
    dataType: DataType.Literal,
    kind: string,
    value: number | string | boolean
}


/********
**** parsing
********/
const valueField = "value"

function valueFromJson(json: any): Value {
    switch (readJsonField(typeField, json)) {
        case "null": return { dataType: DataType.Literal, kind: 'null', value: readJsonField(valueField, json) };
        case "int": return { dataType: DataType.Literal, kind: 'int', value: readJsonField(valueField, json) };
        case "long": return { dataType: DataType.Literal, kind: 'long', value: readJsonField(valueField, json) };
        case "bool": return { dataType: DataType.Literal, kind: 'bool', value: readJsonField(valueField, json) };
        case "string": return { dataType: DataType.Literal, kind: 'string', value: readJsonField(valueField, json) };
        case "char": return { dataType: DataType.Literal, kind: 'char', value: readJsonField(valueField, json) };
        case "byte": return { dataType: DataType.Literal, kind: 'byte', value: readJsonField(valueField, json) };
        case "short": return { dataType: DataType.Literal, kind: 'short', value: readJsonField(valueField, json) };
        case "float": return { dataType: DataType.Literal, kind: 'float', value: readJsonField(valueField, json) };
        case "double": return { dataType: DataType.Literal, kind: 'double', value: readJsonField(valueField, json) };
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
    className: ClassIdentifier,
    pointer: number,
    version: number
}

type StaticReference = {
    dataType: DataType.StaticReference,
    className: ClassIdentifier,
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
const clazzField = "className"

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
                className: readJsonField(clazzField, json),
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
                className: readJsonField(clazzField, json),
                pointer: readJsonField("pointer", json),
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
    dataType: DataType.LocalIdentifier
    parentNodeId: string,
    name: string
}


type FieldIdentifier = {
    dataType: DataType.FieldIdentifier,
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
                dataType: DataType.FieldIdentifier,
                owner: refFromJson(readJsonField("owner", json)),
                name: readJsonField("name", json)
            }
        default:
            return unableToParse(json)
    }
}

function localIdentifierFromJson(json: any): LocalIdentifier {
    switch (readJsonField(typeField, json)) {
        case DataType.LocalIdentifier:
            return {
                dataType: DataType.LocalIdentifier,
                parentNodeId: readJsonField("parent", json),
                name: readJsonField("name", json)
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
    debugger;
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