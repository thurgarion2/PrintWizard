import { scopedContextTransform } from './treeTransforms'
import { initCache, traceCache } from './fetch'
import {
    propagateItemContext,
    aggregateItemState,
    initialContext,
    traceItem
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

function printTree(event){

    function helper(event, depth){
        level(depth, 'event :' + event.kind.type.toString())

        event.executions().forEach(element => {
            switch(element.type){
                case 'ExecutionStep':
                    level(depth+1, 'step : '+element.kind.type.toString())
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
       
        


        const eventWithContext = scopedContextTransform(
            rootEvent,
            initialContext,
            aggregateItemState,
            propagateItemContext);
        
        injectNode.appendChild(traceItem(eventWithContext).html())
    
    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

