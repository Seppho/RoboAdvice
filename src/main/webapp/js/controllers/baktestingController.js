RoboAdviceApp.controller("Backtesting", function ($scope, $log, portfolioService, userService, strategyService) {
    //$log.debug($scope.xAxis);
    $scope.user = userService;

    $scope.loadingInProgress = true;
    // $log.debug($scope.yAxis);

    $scope.update = function(data) {
        //var graphDates = [];
        let formattedDateGraph = [];
        let formattedValueGraph = [];

        // backtesting should let the user to choose a strategy to apply in the past

        $scope.TheInterval = data.interval;
        $log.debug("PortfolioService | Interval: "+ data.interval);
        portfolioService.getBacktesting(data.interval, function(response){
            if(response.statusCode == 0){
                $scope.francesca = new Array(response.data.length);
                $scope.yAxis = new Array(response.data.length);
                //show spinner or other waiting animations
                $scope.loadingInProgress = false;
                $log.debug($scope.loadingInProgress);
                $log.debug("backtesting Controller | callback");
                for(let i = 0;i<response.data.length;i++){
                    //$log.debug(response.data[i].date);
                    $scope.francesca[i] = (new Date(response.data[i].date.year + "/" + response.data[i].date.monthValue + "/" + response.data[i].date.dayOfMonth)).getTime();
                    $scope.yAxis[i] = (response.data[i].totalAmount);
                    //$log.debug(formattedDateGraph[i]);
                    //$log.debug(formattedValueGraph[i]);
                }
                
            }
            else{
                $scope.loadingInProgress = false;
                $log.debug("backtesting Controller | error on backtesting ");
            }
        });
    }
});
