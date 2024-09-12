import { InstanceReference, FieldIdentifier, Value, valueFromJson, ArrayReference, DataType } from "./event"
import { Result, failure, success, objectDataCache } from "./fetch"
export {ObjectData, Field, searchObject, parseObjectStore, Store, ArrayData}

type ObjectData = {
    self: InstanceReference,
    fields: Field[]
}

type Field = {
    identifier: FieldIdentifier,
    value: Value
}

type ArrayData = {
    self : ArrayReference,
    values : Value[]
}

type Store = {
    idToObjects : Map<number, ObjectData[] | ArrayData[]>
}


function parseObjectStore(json : any): Store{
    //we approximatively validate the schema
    let pointerToVersions : Map<number, ObjectData[]> = new Map() 

    for(const obj of json){
    


        if(obj.self.dataType===DataType.InstanceRef){
            let object = obj as ObjectData
            object.fields = object.fields.map(f => {return {
                identifier: f.identifier, 
                value: valueFromJson(f.value)
            }})
        }else if(obj.self.dataType===DataType.ArrayReference){
            let array = obj as ArrayData
            array.values = array.values.map(v => valueFromJson(v))
        }
       

        const pointer = obj.self.pointer
        if(!pointerToVersions.has(pointer)){
            pointerToVersions.set(pointer, [])
        }
    
        let arr = pointerToVersions.get(pointer) as ObjectData[]
        arr.push(obj)
    }
    
    return {
        idToObjects : pointerToVersions
    };

}


// we return the latest object with version<=ref.version
function searchObject(ref : InstanceReference | ArrayReference): Result<ObjectData | ArrayData> {
    let res = objectDataCache().data()
    let store
    if(res.type==='failure'){
        return failure()
    }else{
        store = res.payload
    }
    let versions = store.idToObjects.get(ref.pointer)
    if(versions===undefined)
        return failure()
    versions = versions as ObjectData[] | ArrayData[]

    let latest : ObjectData | ArrayData = versions[0]
    for(const obj of versions){
        if(obj.self.version>ref.version){
            return latest.self.version<=ref.version ? success(latest) : failure();
        }
        latest = obj;
    }
    return success(latest);
}