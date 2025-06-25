package com.wizard.controller;

import com.wizard.common.base.ResultInfo;
import com.wizard.common.enums.ContractTypeEnum;
import com.wizard.common.enums.IntervalEnum;
import com.wizard.common.model.MarketQuotation;
import com.wizard.common.model.SuperTrendSignalResult;
import com.wizard.common.service.SuperTrendSignalService;
import com.wizard.common.utils.ResultInfoUtil;
import com.wizard.model.dto.SymbolLineDTO;
import com.wizard.service.FutureService;
import com.wizard.service.SuperTrendSignalExample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wizard
 * @date 2025年06月23日 18:16
 * @desc
 */
@Slf4j
@RestController
public class SuperTrendSignalController {

    @Resource
    SuperTrendSignalExample superTrendSignalExample;

    @Autowired
    private SuperTrendSignalService superTrendSignalService;

    @GetMapping("/analyzeSignal")
    public void analyzeSignal(String symbol) {
        superTrendSignalExample.analyzeSignal(symbol);
    }

    /**
     * 历史信号分析接口
     * 基于历史K线数据按时间顺序生成历史通知
     *
     * @param symbol        交易对
     * @param timeFrameData 多周期历史数据
     * @return 历史通知列表
     */
    @GetMapping("/analyze-historical")
    public ResultInfo<List<String>> analyzeHistoricalSignals(String symbol) {

        try {
            log.info("开始分析{}的历史信号", symbol);

            // 转换数据格式
            Map<IntervalEnum, List<MarketQuotation>> data = getTimeFrameData(symbol);

            List<String> notifications = analyzeHistoricalSignalsInternal(symbol, data);

            return ResultInfoUtil.buildSuccess("", notifications);

        } catch (Exception e) {
            log.error("历史信号分析失败: {}", e.getMessage(), e);
            return ResultInfoUtil.buildError("历史信号分析失败: " + e.getMessage());
        }
    }

    @Resource
    FutureService futureService;

    private Map<IntervalEnum, List<MarketQuotation>> getTimeFrameData(String symbol){
        SymbolLineDTO symbolLineDTO15MIN = SymbolLineDTO.builder()
                .interval(IntervalEnum.FIFTEEN_MINUTE.getCode())
                .contractType(ContractTypeEnum.PERPETUAL.getCode())
                .symbol(symbol)
                .limit(500)
                .build();
        List<MarketQuotation> continuousKLines15min = futureService.getContinuousKLines(symbolLineDTO15MIN);

        SymbolLineDTO symbolLineDTO1HOUR = SymbolLineDTO.builder()
                .interval(IntervalEnum.ONE_HOUR.getCode())
                .contractType(ContractTypeEnum.PERPETUAL.getCode())
                .symbol(symbol)
                .limit(500)
                .build();
        List<MarketQuotation> continuousKLines1HOUR = futureService.getContinuousKLines(symbolLineDTO1HOUR);

        SymbolLineDTO symbolLineDTO4HOUR = SymbolLineDTO.builder()
                .interval(IntervalEnum.FOUR_HOUR.getCode())
                .contractType(ContractTypeEnum.PERPETUAL.getCode())
                .symbol(symbol)
                .limit(500)
                .build();
        List<MarketQuotation> continuousKLines4HOUR = futureService.getContinuousKLines(symbolLineDTO4HOUR);

        SymbolLineDTO symbolLineDTO1DAY = SymbolLineDTO.builder()
                .interval(IntervalEnum.ONE_DAY.getCode())
                .contractType(ContractTypeEnum.PERPETUAL.getCode())
                .symbol(symbol)
                .limit(500)
                .build();
        List<MarketQuotation> continuousKLines1DAY = futureService.getContinuousKLines(symbolLineDTO1DAY);

        Map<IntervalEnum, List<MarketQuotation>> data = new HashMap<>();
        data.put(IntervalEnum.ONE_DAY, continuousKLines1DAY);
        data.put(IntervalEnum.FOUR_HOUR, continuousKLines4HOUR);
        data.put(IntervalEnum.ONE_HOUR, continuousKLines1HOUR);
        data.put(IntervalEnum.FIFTEEN_MINUTE, continuousKLines15min);

        return data;
    }

    /**
     * 内部历史信号分析方法
     */
    private List<String> analyzeHistoricalSignalsInternal(String symbol,
            Map<IntervalEnum, List<MarketQuotation>> timeFrameData) {

        List<String> notifications = new ArrayList<>();

        // 获取15分钟数据作为主时间轴
        List<MarketQuotation> fifteenMinData = timeFrameData.get(IntervalEnum.FIFTEEN_MINUTE);
        if (fifteenMinData == null || fifteenMinData.isEmpty()) {
            return notifications;
        }

        // 按时间排序
        fifteenMinData.sort(Comparator.comparing(MarketQuotation::getTimestamp));

        SuperTrendSignalService.SignalCombination previousSignal = null;

        // 从第100个数据点开始分析，每20个点分析一次
        int startIndex = Math.min(100, fifteenMinData.size() - 50);

        for (int i = startIndex; i < fifteenMinData.size(); i += 20) {
            LocalDateTime currentTime = fifteenMinData.get(i).getTimestamp();

            try {
                // 构建当前时间点的数据切片
                Map<IntervalEnum, List<MarketQuotation>> currentData = buildDataSlice(timeFrameData, currentTime);

                // 分析当前信号
                SuperTrendSignalResult currentResult = superTrendSignalService.analyzeMultiTimeFrameSignal(symbol,
                        currentData);

                // 检测信号变化
                if (isSignalChanged(previousSignal, currentResult.getSignalCombination())) {
                    String notification = buildNotification(symbol, currentTime, currentResult, previousSignal);
                    notifications.add(notification);

                    log.info("历史信号变化: {} - {}",
                            currentTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                            getChangeDesc(previousSignal, currentResult.getSignalCombination()));
                }

                previousSignal = currentResult.getSignalCombination();

            } catch (Exception e) {
                log.warn("分析历史时间点{}的信号时发生错误: {}", currentTime, e.getMessage());
            }
        }

        log.info("{}历史信号分析完成，共检测到{}个信号变化", symbol, notifications.size());
        return notifications;
    }

    /**
     * 构建数据切片
     */
    private Map<IntervalEnum, List<MarketQuotation>> buildDataSlice(
            Map<IntervalEnum, List<MarketQuotation>> timeFrameData, LocalDateTime targetTime) {

        Map<IntervalEnum, List<MarketQuotation>> slice = new HashMap<>();

        for (Map.Entry<IntervalEnum, List<MarketQuotation>> entry : timeFrameData.entrySet()) {
            List<MarketQuotation> data = entry.getValue();
            if (data != null) {
                List<MarketQuotation> sliceData = data.stream()
                        .filter(q -> q.getTimestamp().isBefore(targetTime) || q.getTimestamp().equals(targetTime))
                        .sorted(Comparator.comparing(MarketQuotation::getTimestamp))
                        .collect(Collectors.toList());
                slice.put(entry.getKey(), sliceData);
            }
        }

        return slice;
    }

    /**
     * 判断信号是否变化
     */
    private boolean isSignalChanged(SuperTrendSignalService.SignalCombination previous,
            SuperTrendSignalService.SignalCombination current) {
        if (previous == null) {
            return current != SuperTrendSignalService.SignalCombination.MIXED_SIGNALS;
        }
        return previous != current;
    }

    /**
     * 构建通知内容
     */
    private String buildNotification(String symbol, LocalDateTime triggerTime,
            SuperTrendSignalResult result, SuperTrendSignalService.SignalCombination previousSignal) {

        StringBuilder content = new StringBuilder();
        content.append(String.format("🔔 【历史信号 - %s】\n", symbol));
        content.append(
                String.format("📅 %s\n", triggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        content.append(String.format("🔄 %s\n\n", getChangeDesc(previousSignal, result.getSignalCombination())));

        content.append("🕐 周期趋势：\n");
        content.append(String.format("├ 日线：%s\n", getTrendDesc(result.getDailyTrend())));
        content.append(String.format("├ 4小时：%s\n", getTrendDesc(result.getFourHourTrend())));
        content.append(String.format("├ 1小时：%s\n", getTrendDesc(result.getOneHourTrend())));
        content.append(String.format("└ 15分钟：%s\n\n", getTrendDesc(result.getFifteenMinTrend())));

        content.append(String.format("%s %s级别\n",
                result.getSignalLevel().getIcon(), result.getSignalLevel().getName()));
        content.append(String.format("%s %s",
                result.getSignalCombination().getEmoji(), result.getSignalCombination().getDescription()));

        return content.toString();
    }

    /**
     * 获取变化描述
     */
    private String getChangeDesc(SuperTrendSignalService.SignalCombination previous,
            SuperTrendSignalService.SignalCombination current) {
        if (previous == null) {
            return String.format("首次信号: %s", getDirection(current));
        }

        String prevDir = getDirection(previous);
        String currDir = getDirection(current);

        if (!prevDir.equals(currDir)) {
            return String.format("方向反转: %s → %s", prevDir, currDir);
        } else {
            return String.format("信号调整: %s → %s", getType(previous), getType(current));
        }
    }

    /**
     * 获取信号方向
     */
    private String getDirection(SuperTrendSignalService.SignalCombination signal) {
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
    private String getType(SuperTrendSignalService.SignalCombination signal) {
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
     * 获取趋势描述
     */
    private String getTrendDesc(Object trend) {
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
