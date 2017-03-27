RoboAdviceApp.component("forecastOne",{
  bindings: {
    startForecast: "&"
  },
  templateUrl: "../../html/forecastView-1.html",
  controller: function($log){
    let $ctrl = this;
    this.$onInit=function(){
        let now = new Date()
        let maxMonth = new Date();

        maxMonth.setMonth(maxMonth.getMonth()+3);
        $('.datepicker').pickadate({
          selectYears: 4, // Creates a dropdown of 14 years to control year
            min: now,
            max: maxMonth,
        });
        $('select').material_select();
    }

    this.nextClick=function(){
      $log.debug("nextClick clicked");
      if($ctrl.forecastDate != null){
        // default forecast
        this.startForecast({date: $ctrl.forecastDate});
      }else{
        // forecast by date
        this.startForecast({date: null})
      }
    }

  }
});
