import { scopedContextTransform } from './treeTransforms'
import { initCache, traceCache } from './fetch'
import {
    propagateItemContext,
    aggregateItemState,
    initialContext,
    toStepGroup
} from './display'
import { parseEventTrace } from './event'


let injectNode = document.getElementById('trace');
let treeViewer = undefined;

function allsteps(event){
    let steps = []
    event.executions().forEach(element => {
        switch(element.type){
            case 'ExecutionStep':
                steps.push(element)
                break;
            case 'Event':
                steps.push(...allsteps(element)) 
        }
    });
    return steps;
}

function printTree(event, upToDepth){

    function helper(event, depth){
        if(depth>upToDepth)
            return;
        level(depth, 'event :' + event.kind.type.toString())

        event.executions().forEach(element => {
            switch(element.type){
                case 'ExecutionStep':
                    let nodedescrp = ''
                    if(element.nodeId.expression!==undefined)
                        nodedescrp = element.nodeId.expression.tokens[0].text
                    level(depth+1, 'step : '+element.kind.type.toString() +' ' +nodedescrp)
                    break;
                case 'Event':
                    helper(element, depth+1)
            }
        });

        level(depth, '===============')
    }

    function level(depth, msg){
        console.log('|-'.repeat(depth)+msg)
    }

    helper(event, 0)
}


Promise.all([initCache()])
    .then(results => {
        let rawTrace = traceCache().data().payload

       
        const rootEvent = parseEventTrace(rawTrace)    
        //debugger;


        const eventWithContext = scopedContextTransform(
            rootEvent,
            initialContext,
            aggregateItemState,
            propagateItemContext);
        const root = toStepGroup(eventWithContext)
        root.expand()
        injectNode.appendChild(root.html)
    
    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

