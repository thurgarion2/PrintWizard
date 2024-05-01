const http = require("http");
const fs = require('fs').promises;

const host = 'localhost';
const port = 8000;



const index = function (req, res) {

    fs.readFile('./index.html')
        .then(contents => {
            res.setHeader("Content-Type", "text/html");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const eventTrace = function (req, res) {

    fs.readFile('./../eventTrace.json')
        .then(contents => {
            res.setHeader("Content-Type", "text/csv");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const sourceFormat = function (req, res) {

    fs.readFile('./../source_format.json')
        .then(contents => {
            res.setHeader("Content-Type", "application/json");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const exposeModule = function (req, res, path) {

    fs.readFile(path)
        .then(contents => {
            res.setHeader("Content-Type", "application/javascript");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};


const requestListener = function (req, res) {
    switch (req.url) {
        case "/":
            index(req, res)
            break
        case "/eventTrace":
            eventTrace(req, res)
            break
        case "/sourceFormat":
            sourceFormat(req, res)
            break
        case "/displayTrace":
            exposeModule(req, res, './modules/displayTrace.js');
            break;
        case "/event":
            exposeModule(req, res, './build/event.js');
            break;
        case "/eventTree":
            exposeModule(req, res, './build/eventTree.js');
            break;
        case "/display":
            exposeModule(req, res, './build/display.js');
            break;
        case "/fetch":
            exposeModule(req, res, './build/fetch.js');
            break;
        default:
            console.log(req.url);
            console.assert(false);
    }
}


const server = http.createServer(requestListener);
server.listen(port, host, () => {
    console.log(`Server is running on http://${host}:${port}`);
});