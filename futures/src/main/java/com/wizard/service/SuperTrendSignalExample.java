package com.wizard.service;

import com.wizard.common.enums.ContractTypeEnum;
import com.wizard.common.enums.IntervalEnum;
import com.wizard.common.model.MarketQuotation;
import com.wizard.common.model.SuperTrendSignalResult;
import com.wizard.common.service.SuperTrendSignalService;
import com.wizard.common.utils.IndicatorCalculateUtil;
import com.wizard.model.dto.SymbolLineDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 超级趋势信号使用示例
 * 演示如何使用超级趋势信号分析功能
 */
@Slf4j
@Service
public class SuperTrendSignalExample {

    @Autowired
    private SuperTrendSignalService signalService;

    /**
     * 使用示例：分析多周期超级趋势信号
     *
     * @param symbol         交易对
     * @param dailyData      日线数据
     * @param fourHourData   4小时数据
     * @param oneHourData    1小时数据
     * @param fifteenMinData 15分钟数据
     * @return 信号分析结果
     */
    public SuperTrendSignalResult analyzeSignalExample(String symbol,
            List<MarketQuotation> dailyData,
            List<MarketQuotation> fourHourData,
            List<MarketQuotation> oneHourData,
            List<MarketQuotation> fifteenMinData) {
        try {
            log.info("开始分析{}的超级趋势信号示例", symbol);

            // 1. 准备多周期数据
            Map<IntervalEnum, List<MarketQuotation>> timeFrameData = new HashMap<>();

            // 2. 直接使用已计算SuperTrend的数据（无需重新计算）
            if (dailyData != null && !dailyData.isEmpty()) {
                timeFrameData.put(IntervalEnum.ONE_DAY, dailyData);
                log.info("加载日线数据：{}条记录", dailyData.size());
            }

            if (fourHourData != null && !fourHourData.isEmpty()) {
                timeFrameData.put(IntervalEnum.FOUR_HOUR, fourHourData);
                log.info("加载4小时数据：{}条记录", fourHourData.size());
            }

            if (oneHourData != null && !oneHourData.isEmpty()) {
                timeFrameData.put(IntervalEnum.ONE_HOUR, oneHourData);
                log.info("加载1小时数据：{}条记录", oneHourData.size());
            }

            if (fifteenMinData != null && !fifteenMinData.isEmpty()) {
                timeFrameData.put(IntervalEnum.FIFTEEN_MINUTE, fifteenMinData);
                log.info("加载15分钟数据：{}条记录", fifteenMinData.size());
            }

            // 验证SuperTrend数据完整性
            validateSupertrendData(timeFrameData);

            // 3. 分析信号
            SuperTrendSignalResult result = signalService.analyzeMultiTimeFrameSignal(symbol, timeFrameData);

            // 4. 输出信号分析结果
            printSignalResult(result);

            return result;

        } catch (Exception e) {
            log.error("分析超级趋势信号时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证SuperTrend数据完整性
     */
    private void validateSupertrendData(Map<IntervalEnum, List<MarketQuotation>> timeFrameData) {
        for (Map.Entry<IntervalEnum, List<MarketQuotation>> entry : timeFrameData.entrySet()) {
            IntervalEnum interval = entry.getKey();
            List<MarketQuotation> quotations = entry.getValue();

            if (quotations != null && !quotations.isEmpty()) {
                MarketQuotation latest = quotations.get(quotations.size() - 1);
                if (latest.getSupertrend() == null) {
                    log.warn("{}周期的SuperTrend指标为空，可能影响信号分析准确性", interval.getName());
                } else {
                    log.info("{}周期SuperTrend验证通过，趋势方向: {}",
                            interval.getName(),
                            latest.getSupertrend().isUptrend() ? "上升" : "下降");
                }
            }
        }
    }

    /**
     * 打印信号分析结果
     */
    private void printSignalResult(SuperTrendSignalResult result) {
        if (result == null) {
            log.warn("信号分析结果为空");
            return;
        }

        log.info("=== 超级趋势信号分析结果 ===");
        log.info("交易对: {}", result.getSymbol());
        log.info("日线趋势: {} {}", result.getDailyTrend().getSymbol(), result.getDailyTrend().getDescription());
        log.info("4小时趋势: {} {}", result.getFourHourTrend().getSymbol(), result.getFourHourTrend().getDescription());
        log.info("1小时趋势: {} {}", result.getOneHourTrend().getSymbol(), result.getOneHourTrend().getDescription());
        log.info("15分钟趋势: {} {}", result.getFifteenMinTrend().getSymbol(),
                result.getFifteenMinTrend().getDescription());
        log.info("信号级别: {} {}", result.getSignalLevel().getIcon(), result.getSignalLevel().getName());
        log.info("策略建议: {}", result.getSignalCombination().getStrategy());
        log.info("具体描述: {}", result.getSignalCombination().getDescription());
        log.info("分析时间: {}", result.getAnalysisTime());

        if (result.getNotification() != null) {
            log.info("通知内容: \n{}", result.getNotification().getContent());
        }
        log.info("=== 分析结果结束 ===");
    }

    /**
     * 简化版分析方法，只需要传入已计算指标的数据
     */
    public SuperTrendSignalResult simpleAnalyze(String symbol, Map<IntervalEnum, List<MarketQuotation>> timeFrameData) {
        return signalService.analyzeMultiTimeFrameSignal(symbol, timeFrameData);
    }

    @Resource
    FutureService futureService;

    public void analyzeSignal(String symbol) {

        SymbolLineDTO symbolLineDTODay = SymbolLineDTO.builder()
                .limit(500)
                .symbol(symbol)
                .contractType(ContractTypeEnum.PERPETUAL.toString())
                .interval(IntervalEnum.ONE_DAY.toString())
                .build();
        List<MarketQuotation> marketQuotationDayList = futureService.getContinuousKLines(symbolLineDTODay);

        SymbolLineDTO symbolLineDTO4H = SymbolLineDTO.builder()
                .limit(500)
                .symbol(symbol)
                .contractType(ContractTypeEnum.PERPETUAL.toString())
                .interval(IntervalEnum.FOUR_HOUR.toString())
                .build();
        List<MarketQuotation> marketQuotationFourHourList = futureService.getContinuousKLines(symbolLineDTO4H);

        SymbolLineDTO symbolLineDTO1H = SymbolLineDTO.builder()
                .limit(500)
                .symbol(symbol)
                .contractType(ContractTypeEnum.PERPETUAL.toString())
                .interval(IntervalEnum.ONE_HOUR.toString())
                .build();
        List<MarketQuotation> marketQuotationOneHourList = futureService.getContinuousKLines(symbolLineDTO1H);

        SymbolLineDTO symbolLineDTO15min = SymbolLineDTO.builder()
                .limit(500)
                .symbol(symbol)
                .contractType(ContractTypeEnum.PERPETUAL.toString())
                .interval(IntervalEnum.FIFTEEN_MINUTE.toString())
                .build();
        List<MarketQuotation> marketQuotation15MinList = futureService.getContinuousKLines(symbolLineDTO15min);

        Map<IntervalEnum, List<MarketQuotation>> timeFrameData = new HashMap<>();
        timeFrameData.put(IntervalEnum.ONE_DAY, marketQuotationDayList);
        timeFrameData.put(IntervalEnum.FOUR_HOUR, marketQuotationFourHourList);
        timeFrameData.put(IntervalEnum.ONE_HOUR, marketQuotationOneHourList);
        timeFrameData.put(IntervalEnum.FIFTEEN_MINUTE, marketQuotation15MinList);

        // 当前信号分析
        simpleAnalyze(symbol, timeFrameData);

        // 历史信号分析
        analyzeHistoricalSignals(symbol, timeFrameData);
    }

    /**
     * 分析历史信号 - 基于500条K线数据按时间顺序生成历史通知
     */
    public void analyzeHistoricalSignals(String symbol, Map<IntervalEnum, List<MarketQuotation>> timeFrameData) {
        log.info("开始分析{}的历史信号变化", symbol);

        // 获取15分钟数据作为主时间轴（数据最密集）
        List<MarketQuotation> fifteenMinData = timeFrameData.get(IntervalEnum.FIFTEEN_MINUTE);
        if (fifteenMinData == null || fifteenMinData.isEmpty()) {
            log.warn("15分钟数据为空，无法进行历史分析");
            return;
        }

        // 按时间排序
        fifteenMinData.sort(Comparator.comparing(MarketQuotation::getTimestamp));

        List<String> historicalNotifications = new ArrayList<>();
        SuperTrendSignalService.SignalCombination previousSignal = null;

        // 从第100个数据点开始分析（确保有足够历史数据）
        int startIndex = Math.min(100, fifteenMinData.size() - 50);

        for (int i = startIndex; i < fifteenMinData.size(); i += 20) { // 每20个点分析一次
            LocalDateTime currentTime = fifteenMinData.get(i).getTimestamp();

            try {
                // 构建当前时间点的数据切片
                Map<IntervalEnum, List<MarketQuotation>> currentData = buildHistoricalDataSlice(timeFrameData,
                        currentTime);

                // 分析当前时间点的信号
                SuperTrendSignalResult currentResult = signalService.analyzeMultiTimeFrameSignal(symbol, currentData);

                // 检测信号变化
                if (isSignalChanged(previousSignal, currentResult.getSignalCombination())) {
                    String notification = buildHistoricalNotification(symbol, currentTime, currentResult,
                            previousSignal);
                    historicalNotifications.add(notification);

                    log.info("历史信号变化: {} - {}",
                            currentTime.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                            getChangeDescription(previousSignal, currentResult.getSignalCombination()));
                }

                previousSignal = currentResult.getSignalCombination();

            } catch (Exception e) {
                log.warn("分析历史时间点{}的信号时发生错误: {}", currentTime, e.getMessage());
            }
        }

        // 输出历史通知汇总
        log.info("=== {} 历史信号通知汇总 ===", symbol);
        log.info("总共检测到 {} 个信号变化", historicalNotifications.size());

        for (int i = 0; i < historicalNotifications.size(); i++) {
            log.info("通知 {}: \n{}", i + 1, historicalNotifications.get(i));
            log.info("----------------------------------------");
        }
    }

    /**
     * 构建历史数据切片
     */
    private Map<IntervalEnum, List<MarketQuotation>> buildHistoricalDataSlice(
            Map<IntervalEnum, List<MarketQuotation>> timeFrameData, LocalDateTime targetTime) {

        Map<IntervalEnum, List<MarketQuotation>> slice = new HashMap<>();

        for (Map.Entry<IntervalEnum, List<MarketQuotation>> entry : timeFrameData.entrySet()) {
            List<MarketQuotation> data = entry.getValue();
            if (data != null) {
                List<MarketQuotation> sliceData = data.stream()
                        .filter(q -> q.getTimestamp().isBefore(targetTime) || q.getTimestamp().equals(targetTime))
                        .sorted(Comparator.comparing(MarketQuotation::getTimestamp))
                        .collect(java.util.stream.Collectors.toList());
                slice.put(entry.getKey(), sliceData);
            }
        }

        return slice;
    }

    /**
     * 判断信号是否发生变化
     */
    private boolean isSignalChanged(SuperTrendSignalService.SignalCombination previous,
            SuperTrendSignalService.SignalCombination current) {
        if (previous == null) {
            // 首次信号且非观望状态
            return current != SuperTrendSignalService.SignalCombination.MIXED_SIGNALS;
        }

        // 信号组合发生变化
        return previous != current;
    }

    /**
     * 构建历史通知内容
     */
    private String buildHistoricalNotification(String symbol, LocalDateTime triggerTime,
            SuperTrendSignalResult result,
            SuperTrendSignalService.SignalCombination previousSignal) {

        StringBuilder content = new StringBuilder();
        content.append(String.format("🔔 【历史信号 - %s】\n", symbol));
        content.append(String.format("📅 %s\n",
                triggerTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        content.append(String.format("🔄 %s\n\n", getChangeDescription(previousSignal, result.getSignalCombination())));

        content.append("🕐 周期趋势：\n");
        content.append(String.format("├ 日线：%s\n", getTrendSymbol(result.getDailyTrend())));
        content.append(String.format("├ 4小时：%s\n", getTrendSymbol(result.getFourHourTrend())));
        content.append(String.format("├ 1小时：%s\n", getTrendSymbol(result.getOneHourTrend())));
        content.append(String.format("└ 15分钟：%s\n\n", getTrendSymbol(result.getFifteenMinTrend())));

        content.append(String.format("%s %s级别\n",
                result.getSignalLevel().getIcon(), result.getSignalLevel().getName()));
        content.append(String.format("%s %s",
                result.getSignalCombination().getEmoji(), result.getSignalCombination().getDescription()));

        return content.toString();
    }

    /**
     * 获取变化描述
     */
    private String getChangeDescription(SuperTrendSignalService.SignalCombination previous,
            SuperTrendSignalService.SignalCombination current) {
        if (previous == null) {
            return String.format("首次信号: %s", getSignalDirection(current));
        }

        String prevDirection = getSignalDirection(previous);
        String currDirection = getSignalDirection(current);

        if (!prevDirection.equals(currDirection)) {
            return String.format("方向反转: %s → %s", prevDirection, currDirection);
        } else {
            return String.format("信号调整: %s → %s", getSignalType(previous), getSignalType(current));
        }
    }

    /**
     * 获取信号方向
     */
    private String getSignalDirection(SuperTrendSignalService.SignalCombination signal) {
        switch (signal) {
            case STRONG_BULLISH:
            case PULLBACK_IN_BULL:
            case SHORT_TERM_BOUNCE:
            case HOURLY_STRONG_BUT_DIVERGENT:
            case POTENTIAL_BOTTOM_REVERSAL:
            case POTENTIAL_REVERSAL_ATTEMPT:
                return "多头";
            case STRONG_BEARISH:
            case SHORT_TERM_REBOUND:
                return "空头";
            default:
                return "观望";
        }
    }

    /**
     * 获取信号类型
     */
    private String getSignalType(SuperTrendSignalService.SignalCombination signal) {
        switch (signal) {
            case STRONG_BULLISH:
                return "强势多头";
            case PULLBACK_IN_BULL:
                return "回调多头";
            case SHORT_TERM_BOUNCE:
                return "短线反弹";
            case HOURLY_STRONG_BUT_DIVERGENT:
                return "背离多头";
            case STRONG_BEARISH:
                return "强势空头";
            case SHORT_TERM_REBOUND:
                return "短线反弹";
            case POTENTIAL_REVERSAL_ATTEMPT:
                return "反转尝试";
            case POTENTIAL_BOTTOM_REVERSAL:
                return "底部反转";
            case PULLBACK_CONFIRMATION:
                return "回调确认";
            default:
                return "信号混乱";
        }
    }

    /**
     * 获取趋势符号
     */
    private String getTrendSymbol(Object trend) {
        if (trend == null)
            return "/ 不明确";
        String trendStr = trend.toString();
        if (trendStr.contains("UP"))
            return "↑ 上升";
        if (trendStr.contains("DOWN"))
            return "↓ 下降";
        return "/ 不明确";
    }
}
