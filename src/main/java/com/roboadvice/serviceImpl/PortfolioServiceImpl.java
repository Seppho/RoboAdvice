package com.roboadvice.serviceImpl;

import com.roboadvice.dto.BacktestingDTO;
import com.roboadvice.dto.PortfolioDTO;
import com.roboadvice.model.*;
import com.roboadvice.repository.*;
import com.roboadvice.service.PortfolioService;
import com.roboadvice.utils.Constant;
import org.apache.tomcat.jni.Local;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioServiceImpl implements PortfolioService{

    private PortfolioRepository portfolioRepository;
    private UserRepository userRepository;
    private StrategyRepository strategyRepository;
    private AssetsRepository assetsRepository;
    private ApiDataRepository apiDataRepository;
    private AssetsClassRepository assetsClassRepository;

    @Autowired
    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                UserRepository userRepository,
                                StrategyRepository strategyRepository,
                                AssetsRepository assetsRepository,
                                ApiDataRepository apiDataRepository,
                                AssetsClassRepository assetsClassRepository) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.strategyRepository = strategyRepository;
        this.assetsRepository = assetsRepository;
        this.apiDataRepository = apiDataRepository;
        this.assetsClassRepository = assetsClassRepository;
    }

    @Override
    public PortfolioDTO getCurrent(String userEmail) {
        User u = userRepository.findByEmail(userEmail);
        if(u != null){
            List<Portfolio> portfolioList = portfolioRepository.getCurrent(u);
            if(portfolioList != null && !portfolioList.isEmpty()){
                PortfolioDTO portfolioDTO = new PortfolioDTO();
                portfolioDTO.setTotalAmount(BigDecimal.ZERO);
                for(Portfolio p : portfolioList){
                    portfolioDTO.setTotalAmount(portfolioDTO.getTotalAmount().add(p.getValue()));
                }
                for(int i = 0; i< Constant.NUM_ASSETS_CLASS; i++){
                    portfolioDTO.setAssetsClassAmount(i+1, portfolioList.get(i).getValue() );
                    portfolioDTO.setAssetsClassPercentage(i+1, portfolioDTO.getAssetsClassAmount(i+1).multiply(new BigDecimal(100)).divide(portfolioDTO.getTotalAmount(), 2, RoundingMode.HALF_UP));
                }
                portfolioDTO.setDate(portfolioList.get(0).getDate());
                return portfolioDTO;
            }
            else
                return null;
        }
        else
            return null;
    }


    @Override
    @Cacheable("portfolioFullHistory")
    public List<PortfolioDTO> getFullHistory(String userEmail) {

        User u = userRepository.findByEmail(userEmail);
        if(u != null) {
            List<Portfolio> portfolioList = portfolioRepository.fullHistoryByUser(u);

            if(portfolioList != null && !portfolioList.isEmpty()){
                List<PortfolioDTO> portfolioDTO_list = new ArrayList<>();
                PortfolioDTO pDTO = new PortfolioDTO();
                for(int i=0; i<portfolioList.size();i+=Constant.NUM_ASSETS_CLASS) {
                    pDTO = new PortfolioDTO();

                    pDTO.setDate(portfolioList.get(i).getDate());

                    pDTO.setTotalAmount(BigDecimal.ZERO);

                    for(int j=i;j<i+Constant.NUM_ASSETS_CLASS;j++) {
                        pDTO.setTotalAmount(pDTO.getTotalAmount().add(portfolioList.get(j).getAmount()));
                        pDTO.setAssetsClassAmount(portfolioList.get(j).getAssetsClass().getId(), portfolioList.get(j).getAmount());
                    }
                    for(int y=i;y<i+Constant.NUM_ASSETS_CLASS;y++) {
                        pDTO.setAssetsClassPercentage(portfolioList.get(y).getAssetsClass().getId(), portfolioList.get(y).getAmount().multiply(new BigDecimal(100).divide(pDTO.getTotalAmount(), 2, RoundingMode.HALF_UP)));
                    }
                    portfolioDTO_list.add(pDTO);
                }
                return portfolioDTO_list;
            }
            else {
                return null;
            }
        }
        else
            return null;


    }

    @Override
    public List<PortfolioDTO> getHistoryByDates(String userEmail, LocalDate fromDate, LocalDate toDate) {
        User u = userRepository.findByEmail(userEmail);
        if(u != null){
            List<Portfolio> portfolioList = portfolioRepository.historyByUserAndDates(u, fromDate.toString(), toDate.toString());

            if (portfolioList != null && !portfolioList.isEmpty()) {
                List<PortfolioDTO> portfolioDTO_list = new ArrayList<>();

                PortfolioDTO pDTO = new PortfolioDTO();
                for(int i=0; i<portfolioList.size();i+=Constant.NUM_ASSETS_CLASS) {
                    pDTO = new PortfolioDTO();

                    pDTO.setDate(portfolioList.get(i).getDate());

                    pDTO.setTotalAmount(BigDecimal.ZERO);

                    for (int j = i; j < i + Constant.NUM_ASSETS_CLASS; j++) {
                        pDTO.setTotalAmount(pDTO.getTotalAmount().add(portfolioList.get(j).getAmount()));
                        pDTO.setAssetsClassAmount(portfolioList.get(j).getAssetsClass().getId(), portfolioList.get(j).getAmount());
                    }
                    for (int y = i; y < i + Constant.NUM_ASSETS_CLASS; y++) {
                        pDTO.setAssetsClassPercentage(portfolioList.get(y).getAssetsClass().getId(), portfolioList.get(y).getAmount().multiply(new BigDecimal(100).divide(pDTO.getTotalAmount(), 2, RoundingMode.HALF_UP)));
                    }
                    portfolioDTO_list.add(pDTO);
                }
                return portfolioDTO_list;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public List<BacktestingDTO> getBackTestingChart(String userEmail, LocalDate startDate) {
        User u = userRepository.findByEmail(userEmail);
        if(u==null)
            return null;

        List<Strategy> currentStrategyList = strategyRepository.findByUserAndActive(u, true);
        if(currentStrategyList==null || currentStrategyList.isEmpty())
            return null;


        List<BacktestingDTO> backtestingDTOList = new ArrayList<>();
        BacktestingDTO bDTO;

        List<Portfolio> portfolioList = new ArrayList<>();
        Portfolio p;

        BigDecimal investment = new BigDecimal(Constant.INITIAL_INVESTMENT);
        BigDecimal amount=BigDecimal.ZERO, value=BigDecimal.ZERO, units=BigDecimal.ZERO;
        LocalDate endDate = LocalDate.now();
        List<Assets> assetsList = (List<Assets>) assetsRepository.findAll();
        List<AssetsClass> assetsClassList = (List<AssetsClass>) assetsClassRepository.findAll();
        ApiData api;

        //Create first portfolio========================================================================
        for(Strategy str : currentStrategyList){
            for(Assets asset : assetsList){
                if(str.getAssetsClass().getId() == asset.getAssetsClass().getId()){
                    amount = Constant.percentage(investment, str.getPercentage());
                    value = Constant.percentage(amount, asset.getAllocation_p());

                    api = apiDataRepository.findTopByAssetsAndDateLessThanEqualOrderByDateDesc(asset, startDate);

                    if (api != null)
                        units = value.divide(api.getValue(), 5, RoundingMode.HALF_UP);
                    else {
                        units = BigDecimal.ZERO;
                        value = BigDecimal.ZERO;
                    }

                    p = new Portfolio();
                    p.setDate(startDate.plusDays(1));
                    p.setAssetsClass(str.getAssetsClass());
                    p.setAssets(asset);
                    p.setAmount(amount);
                    p.setValue(value);
                    p.setUnits(units);

                    portfolioList.add(p);
                }
            }
        }
        System.out.println("First portfolio created");


        //Update portfolio every day===================================================================
        int index=0;

        List<Portfolio> oldPortfolioList = new ArrayList<>();

        for(LocalDate date = startDate.plusDays(1); date.isBefore(endDate); date = date.plusDays(2)){
            oldPortfolioList.clear();
            for( ;index<portfolioList.size();index++){
                oldPortfolioList.add(portfolioList.get(index));
            }

            List<ApiData> apiDataList = apiDataRepository.findLatestApiValuesByDate(date.toString());
            for(int i=0;i<Constant.NUM_ASSETS;i++){
                //api = apiDataRepository.findTopByAssetsAndDateLessThanEqualOrderByDateDesc(assetsList.get(i), date);
                api = apiDataList.get(Constant.NUM_ASSETS-i-1);
                value = oldPortfolioList.get(i).getUnits().multiply(api.getValue());
                p = new Portfolio();
                p.setDate(date.plusDays(1));
                p.setUnits(oldPortfolioList.get(i).getUnits());
                p.setValue(value);
                portfolioList.add(p);
            }
        }

        //Create list of BacktestingDTO====================================================================
        for(int i=0; i<portfolioList.size();i+=Constant.NUM_ASSETS) {
            bDTO = new BacktestingDTO();

            bDTO.setDate(portfolioList.get(i).getDate());

            bDTO.setTotalAmount(BigDecimal.ZERO);

            for(int j=i;j<i+Constant.NUM_ASSETS && j<portfolioList.size();j++) {
                bDTO.setTotalAmount(bDTO.getTotalAmount().add(portfolioList.get(j).getValue()));
            }
            backtestingDTOList.add(bDTO);
        }

        return  backtestingDTOList;

    }


    @Override
    public PortfolioDTO getAdvice(String userEmail, int strategyCode) {
        User u = userRepository.findByEmail(userEmail);
        if(u==null)
            return null;

        List<Strategy> currentStrategyList = strategyRepository.findByUserAndActive(u, true);
        if(currentStrategyList == null || currentStrategyList.isEmpty())
            return null;

        List<Strategy> strategyList=new ArrayList<>();
        List<AssetsClass> assetsClassList = (List<AssetsClass>) assetsClassRepository.findAll();
        Strategy s;
        if(strategyCode==0){
            s = new Strategy(0, "Bonds", LocalDate.now(), true, new BigDecimal(95), u, assetsClassList.get(0));
            strategyList.add(s);
            s = new Strategy(0, "Bonds", LocalDate.now(), true, new BigDecimal(0), u, assetsClassList.get(1));
            strategyList.add(s);
            s = new Strategy(0, "Bonds", LocalDate.now(), true, new BigDecimal(0), u, assetsClassList.get(2));
            strategyList.add(s);
            s = new Strategy(0, "Bonds", LocalDate.now(), true, new BigDecimal(5), u, assetsClassList.get(3));
            strategyList.add(s);
        }
        else if(strategyCode==1){
            s = new Strategy(0, "Income", LocalDate.now(), true, new BigDecimal(65), u, assetsClassList.get(0));
            strategyList.add(s);
            s = new Strategy(0, "Income", LocalDate.now(), true, new BigDecimal(10), u, assetsClassList.get(1));
            strategyList.add(s);
            s = new Strategy(0, "Income", LocalDate.now(), true, new BigDecimal(15), u, assetsClassList.get(2));
            strategyList.add(s);
            s = new Strategy(0, "Income", LocalDate.now(), true, new BigDecimal(10), u, assetsClassList.get(3));
            strategyList.add(s);
        }
        else if(strategyCode==2){
            s = new Strategy(0, "Balanced", LocalDate.now(), true, new BigDecimal(30), u, assetsClassList.get(0));
            strategyList.add(s);
            s = new Strategy(0, "Balanced", LocalDate.now(), true, new BigDecimal(30), u, assetsClassList.get(1));
            strategyList.add(s);
            s = new Strategy(0, "Balanced", LocalDate.now(), true, new BigDecimal(20), u, assetsClassList.get(2));
            strategyList.add(s);
            s = new Strategy(0, "Balanced", LocalDate.now(), true, new BigDecimal(20), u, assetsClassList.get(3));
            strategyList.add(s);
        }
        else if(strategyCode==3){
            s = new Strategy(0, "Growth", LocalDate.now(), true, new BigDecimal(20), u, assetsClassList.get(0));
            strategyList.add(s);
            s = new Strategy(0, "Growth", LocalDate.now(), true, new BigDecimal(60), u, assetsClassList.get(1));
            strategyList.add(s);
            s = new Strategy(0, "Growth", LocalDate.now(), true, new BigDecimal(10), u, assetsClassList.get(2));
            strategyList.add(s);
            s = new Strategy(0, "Growth", LocalDate.now(), true, new BigDecimal(10), u, assetsClassList.get(3));
            strategyList.add(s);
        }
        else if(strategyCode==4){
            s = new Strategy(0, "Stocks", LocalDate.now(), true, new BigDecimal(0), u, assetsClassList.get(0));
            strategyList.add(s);
            s = new Strategy(0, "Stocks", LocalDate.now(), true, new BigDecimal(100), u, assetsClassList.get(1));
            strategyList.add(s);
            s = new Strategy(0, "Stocks", LocalDate.now(), true, new BigDecimal(0), u, assetsClassList.get(2));
            strategyList.add(s);
            s = new Strategy(0, "Stocks", LocalDate.now(), true, new BigDecimal(0), u, assetsClassList.get(3));
            strategyList.add(s);
        }
        else return null;

        LocalDate startDate = currentStrategyList.get(0).getDate();
        LocalDate endDate = LocalDate.now();
        BigDecimal startAmount = portfolioRepository.findTotalAmountByUserAndDate(u, startDate);
        if(startAmount==null)
            startAmount = new BigDecimal(10000);
        BigDecimal amount=BigDecimal.ZERO, value=BigDecimal.ZERO, units=BigDecimal.ZERO;
        List<Assets> assetsList = (List<Assets>) assetsRepository.findAll();
        ApiData api;
        List<Portfolio> portfolioList = new ArrayList<>();
        Portfolio p;

        //=====================Generate first portfolio and first amount
        for(Strategy str : strategyList){
            for(Assets asset : assetsList){
                if(str.getAssetsClass().getId() == asset.getAssetsClass().getId()){
                    amount = Constant.percentage(startAmount, str.getPercentage());
                    value = Constant.percentage(amount, asset.getAllocation_p());

                    //api = apiDataRepository.findLatestValueByAssetAndDate(asset.getId(), startDate.toString());
                    api = apiDataRepository.findTopByAssetsAndDateLessThanEqualOrderByDateDesc(asset, startDate);

                    if(api != null)
                        units = value.divide(api.getValue(), 5, RoundingMode.HALF_UP);
                    else{
                        units = BigDecimal.ZERO;
                        value = BigDecimal.ZERO;
                    }

                    p=new Portfolio();
                    p.setDate(startDate.plusDays(1));
                    p.setAssetsClass(str.getAssetsClass());
                    p.setAssets(asset);
                    p.setAmount(amount);
                    p.setUnits(units);

                    portfolioList.add(p);
                }
            }
        }

        //======================================Generate all the others portfolios
        amount = BigDecimal.ZERO;
        int index=0;
        List<Portfolio> oldPortfolioList = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        for(LocalDate date = startDate.plusDays(1);date.isBefore(endDate);date = date.plusDays(2)){
            oldPortfolioList.clear();
            for( ;index<portfolioList.size();index++){
                oldPortfolioList.add(portfolioList.get(index));
            }

            List<ApiData> apiDataList= apiDataRepository.findLatestApiValuesByDate(date.toString());
            for(int i=0;i<Constant.NUM_ASSETS;i++){
                api = apiDataList.get(Constant.NUM_ASSETS-i-1);
                value = oldPortfolioList.get(i).getUnits().multiply(api.getValue());
                p = new Portfolio();
                p.setDate(date.plusDays(1));
                p.setUnits(oldPortfolioList.get(i).getUnits());
                p.setValue(value);
                portfolioList.add(p);
            }
        }

        PortfolioDTO pDTO = new PortfolioDTO();
        BigDecimal totalAmount=BigDecimal.ZERO;

        for(int j=portfolioList.size()-Constant.NUM_ASSETS;j<portfolioList.size();j++)
            totalAmount = totalAmount.add(portfolioList.get(j).getValue());

        pDTO.setTotalAmount(totalAmount);

        return pDTO;
    }
}
