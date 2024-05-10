import { scopedTreeTransform } from './treeTransforms'
import { initCache, traceCache } from './fetch'
import { 
    isDisplayed, 
    labelItem, 
    propagateItemContext, 
    aggregateItemState, 
    initialContext 
} from './display'
import { parseLabels, parseRawLabels } from './event'


let injectNode = document.getElementById('trace');
let treeViewer = undefined;


Promise.all([initCache()])
    .then(results => {
        let rawTrace = traceCache().data().payload
       
        const rawLabels = parseRawLabels(rawTrace)
        const labels = parseLabels(rawLabels)
       
        const labelsWithContext = scopedTreeTransform(
            labels,
            initialContext,
            aggregateItemState,
            propagateItemContext);
        const labelsItems = 
            labelsWithContext
                .filter(isDisplayed)
                .map(labelItem)
        
        labelsItems.map( item => injectNode.appendChild(item.html()))
       


        // const traceTree = fromJson(rawTrace);

        // const painter = {
        //     paint : (node) => injectNode.appendChild(node)
        // };

        // let objects = [];
        // const exploreObject = vbox(() => {
        //     return objects.map(objectElement);
        // })

        // const traceNode = traceElement(traceTree, {addObject : (obj) => {
        //     objects.unshift(obj);
        //     exploreObject.update();
        // }});

        // const app = hbox(() => {return [traceNode, exploreObject]})
        // app.display(painter);

    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

