var myFunc = function (
  successHandler, 
  failureHandler, 
  url, prefer) {
  cordova.exec(
      successHandler, 
      failureHandler, 
      "DocumentHandler", 
      "HandleDocumentWithURL", 
      [{"url" : url, "prefer": prefer}]);
};

window.handleDocumentWithURL = myFunc;

if(module && module.exports) {
  module.exports = myFunc;
}

