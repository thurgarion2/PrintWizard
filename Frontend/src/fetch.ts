export {initCache, sourceCodeCache, traceCache, objectDataCache}


/*******************************************************
 **************** Types ******************
 *******************************************************/

type Result<A> = Success<A> | Failure
type Success<A> = {
    type : 'success',
    payload : A
}
type Failure = {
    type : 'failure'
}

interface DataCache<A>{
    data : () => Result<A>
}



/*******************************************************
 **************** API ******************
 *******************************************************/

/**************
 ********* state
 **************/

let sourceCode : DataCache<any> 
let trace : DataCache<any>
let objectData : DataCache<any>

/**************
 ********* Functions
 **************/

async function initCache(){
    sourceCode = await loadAllData(fetchSourceFormat)
    trace = await loadAllData(fetchEventTrace)
    objectData = await loadAllData(fetchSourceObjectData)
}


function sourceCodeCache() : DataCache<any> {
    return sourceCode
}

function traceCache() : DataCache<any> {
    return trace
}

function objectDataCache() : DataCache<any> {
    return objectData
}


/*******************************************************
 **************** Private methods ******************
 *******************************************************/

/**************
 ********* different cache types
 **************/

async function loadAllData<A>(load : () => Promise<A>):Promise<DataCache<A>>{
    try{
        const payload = await load()
        return {
            data : () => success(payload)
        }
    }catch(error){
        return {
            data : () => failure()
        }
    }
}

/**************
 ********* fetch file
 **************/

function fetchEventTrace() {
    return fetch('eventTrace')
        .then(response => {
            return response.json()
        })
}

function fetchSourceFormat() {
    return fetch('sourceFormat')
        .then(response => {
            return response.json()
        })
}

function fetchSourceObjectData() {
    return fetch('objectData')
        .then(response => {
            return response.json()
        })
}

/**************
 ********* constructors
 **************/

function success<A>(data : A) : Success<A>{
    return {
        type : 'success',
        payload : data
    }
}

function failure() : Failure{
    return {
        type : 'failure'
    }
}


