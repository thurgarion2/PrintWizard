import { fromJson } from './eventTree'
import { initCache, traceCache } from './fetch'
import {traceElement, hbox, vbox, objectElement} from './display'


let injectNode = document.getElementById('trace');
let treeViewer = undefined;


Promise.all([initCache()])
    .then(results => {
        let rawTrace = traceCache().data().payload


        const traceTree = fromJson(rawTrace);
    
        const painter = {
            paint : (node) => injectNode.appendChild(node)
        };

        let objects = [];
        const exploreObject = vbox(() => {
            return objects.map(objectElement);
        })

        const traceNode = traceElement(traceTree, {addObject : (obj) => {
            objects.unshift(obj);
            exploreObject.update();
        }});

        const app = hbox(() => {return [traceNode, exploreObject]})
        app.display(painter);

    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

