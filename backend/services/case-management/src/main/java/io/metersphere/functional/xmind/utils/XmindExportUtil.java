package io.metersphere.functional.xmind.utils;

import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.xmind.domain.FunctionalCaseXmindDTO;
import io.metersphere.functional.xmind.domain.FunctionalCaseXmindData;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.Translator;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.xmind.core.*;
import org.xmind.core.style.IStyle;
import org.xmind.core.style.IStyleSheet;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wx
 */
public class XmindExportUtil {


    /**
     * 下载xmind模板
     *
     * @param response
     * @param caseData
     * @param template
     */
    public static void downloadTemplate(HttpServletResponse response, FunctionalCaseXmindData caseData, boolean template) {
        IWorkbook workBook = createXmindByCaseData(caseData, template);

        response.setContentType("application/octet-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(Translator.get("functional_case_xmind_template"), StandardCharsets.UTF_8.name()) + ".xmind");
            workBook.save(response.getOutputStream());
        } catch (UnsupportedEncodingException e) {
            LogUtils.error(e.getMessage(), e);
            throw new MSException("Utf-8 encoding is not supported");
        } catch (Exception e) {
            LogUtils.error(e.getMessage(), e);
            throw new MSException("IO exception");
        }
    }

    private static IWorkbook createXmindByCaseData(FunctionalCaseXmindData caseData, boolean template) {
        // 创建思维导图的工作空间
        IWorkbookBuilder workbookBuilder = Core.getWorkbookBuilder();
        IWorkbook workbook = workbookBuilder.createWorkbook();

        Map<String, IStyle> styleMap = initTheme(workbook);

        // 获得默认sheet
        ISheet primarySheet = workbook.getPrimarySheet();
        if (styleMap.containsKey("mapStyle")) {
            primarySheet.setStyleId(styleMap.get("mapStyle").getId());
        }
        // 获得根主题
        ITopic rootTopic = primarySheet.getRootTopic();
        if (styleMap.containsKey("centralTopicStyle")) {
            rootTopic.setStyleId(styleMap.get("centralTopicStyle").getId());
        }
        // 设置根主题的标题
        rootTopic.setTitleText(Translator.get("functional_case"));

        if (CollectionUtils.isNotEmpty(caseData.getChildren())) {
            for (FunctionalCaseXmindData data : caseData.getChildren()) {
                addItemTopic(rootTopic, workbook, styleMap, data, true, template);
            }
        }
        return workbook;
    }

    private static void addItemTopic(ITopic parentTpoic, IWorkbook workbook, Map<String, IStyle> styleMap, FunctionalCaseXmindData xmindData, boolean isFirstLevel, boolean template) {
        ITopic topic = workbook.createTopic();
        topic.setTitleText(xmindData.getModuleName());
        if (isFirstLevel) {
            if (styleMap.containsKey("mainTopicStyle")) {
                topic.setStyleId(styleMap.get("mainTopicStyle").getId());
            }
        } else {
            if (styleMap.containsKey("subTopicStyle")) {
                topic.setStyleId(styleMap.get("subTopicStyle").getId());
            }
        }
        parentTpoic.add(topic);

        if (CollectionUtils.isNotEmpty(xmindData.getFunctionalCaseList())) {
            IStyle style = null;
            if (styleMap.containsKey("subTopicStyle")) {
                style = styleMap.get("subTopicStyle");
            }
            for (FunctionalCaseXmindDTO dto : xmindData.getFunctionalCaseList()) {
                // 创建小节节点
                ITopic itemTopic = workbook.createTopic();
                if (style != null) {
                    itemTopic.setStyleId(style.getId());
                }
                if (template) {
                    // 模板
                    buildTemplateTopic(topic, style, dto, itemTopic, workbook);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(xmindData.getChildren())) {
            for (FunctionalCaseXmindData data : xmindData.getChildren()) {
                addItemTopic(topic, workbook, styleMap, data, false, template);
            }
        }
    }

    private static void buildTemplateTopic(ITopic topic, IStyle style, FunctionalCaseXmindDTO dto, ITopic itemTopic, IWorkbook workbook) {

        //用例名称
        itemTopic.setTitleText("case-P0: " + dto.getName());

        //前置条件
        if (StringUtils.isNotBlank(dto.getPrerequisite())) {
            ITopic preTopic = workbook.createTopic();
            preTopic.setTitleText(Translator.get("xmind_prerequisite") + ": " + dto.getPrerequisite());
            if (style != null) {
                preTopic.setStyleId(style.getId());
            }
            itemTopic.add(preTopic, ITopic.ATTACHED);
        }

        //备注
        if (StringUtils.isNotBlank(dto.getDescription())) {
            ITopic deTopic = workbook.createTopic();
            deTopic.setTitleText(Translator.get("xmind_description") + ": " + dto.getDescription());
            if (style != null) {
                deTopic.setStyleId(style.getId());
            }
            itemTopic.add(deTopic, ITopic.ATTACHED);
        }

        //标签
        if (StringUtils.isNotBlank(dto.getTags())) {
            try {
                List<String> arr = JSON.parseArray(dto.getTags());
                String tagStr = StringUtils.EMPTY;
                for (int i = 0; i < arr.size(); i++) {
                    tagStr = tagStr + arr.get(i) + "|";
                }
                if (tagStr.endsWith("|")) {
                    tagStr = tagStr.substring(0, tagStr.length() - 1);
                }
                ITopic tagTopic = workbook.createTopic();
                tagTopic.setTitleText(Translator.get("xmind_tags") + ":" + tagStr);
                if (style != null) {
                    tagTopic.setStyleId(style.getId());
                }
                itemTopic.add(tagTopic, ITopic.ATTACHED);
            } catch (Exception e) {
            }
        }

        if (StringUtils.equalsIgnoreCase(dto.getCaseEditType(), FunctionalCaseTypeConstants.CaseEditType.TEXT.name())) {
            //文本描述
            ITopic textDesTopic = workbook.createTopic();
            String desc = dto.getTextDescription();
            textDesTopic.setTitleText(desc == null ? Translator.get("xmind_textDescription") + ": " : Translator.get("xmind_textDescription") + ": " + desc);
            if (style != null) {
                textDesTopic.setStyleId(style.getId());
            }

            String result = dto.getExpectedResult();
            ITopic resultTopic = workbook.createTopic();
            resultTopic.setTitleText(result == null ? Translator.get("xmind_expectedResult") + ": " : Translator.get("xmind_expectedResult") + ": " + result);
            if (style != null) {
                resultTopic.setStyleId(style.getId());
            }
            textDesTopic.add(resultTopic, ITopic.ATTACHED);

            if (StringUtils.isNotEmpty(desc) || StringUtils.isNotEmpty(result)) {
                itemTopic.add(textDesTopic, ITopic.ATTACHED);
            }
        } else {
            //步骤描述
            try {
                ITopic stepDesTopic = workbook.createTopic();
                stepDesTopic.setTitleText(Translator.get("xmind_stepDescription"));
                if (style != null) {
                    stepDesTopic.setStyleId(style.getId());
                }
                List<Map> arr = JSON.parseArray(dto.getSteps());
                for (int i = 0; i < arr.size(); i++) {
                    Map<String, String> obj = arr.get(i);
                    if (obj.containsKey("desc")) {
                        ITopic stepTopic = workbook.createTopic();
                        String desc = obj.get("desc");
                        stepTopic.setTitleText(Translator.get("xmind_step") + ": " + desc);
                        if (style != null) {
                            stepTopic.setStyleId(style.getId());
                        }

                        boolean hasResult = false;
                        if (obj.containsKey("result")) {
                            String result = obj.get("result");
                            if (StringUtils.isNotEmpty(result)) {
                                hasResult = true;
                                ITopic resultTopic = workbook.createTopic();
                                resultTopic.setTitleText(Translator.get("xmind_expectedResult") + ": " + result);
                                if (style != null) {
                                    resultTopic.setStyleId(style.getId());
                                }
                                stepTopic.add(resultTopic, ITopic.ATTACHED);
                            }
                        }

                        if (StringUtils.isNotEmpty(desc) || hasResult) {
                            stepDesTopic.add(stepTopic, ITopic.ATTACHED);
                        }

                    }
                }
                itemTopic.add(stepDesTopic, ITopic.ATTACHED);
            } catch (Exception e) {
            }
        }

        //自定义字段
        dto.getTemplateCustomFieldDTOList().forEach(item -> {
            if (!StringUtils.equalsIgnoreCase(item.getFieldName(), "functional_priority")) {
                ITopic customTopic = workbook.createTopic();
                customTopic.setTitleText(item.getFieldName() + ":");
                if (style != null) {
                    customTopic.setStyleId(style.getId());
                }
                itemTopic.add(customTopic, ITopic.ATTACHED);
            }
        });

        topic.add(itemTopic);
    }


    private static Map<String, IStyle> initTheme(IWorkbook workbook) {
        Map<String, IStyle> styleMap = new HashMap<>();

        IStyleSheet styleSheet = workbook.getStyleSheet();
        IStyle mapStyle = styleSheet.createStyle(IStyle.MAP);
        mapStyle.setProperty("line-tapered", "none");
        mapStyle.setProperty("multi-line-colors", "none");
        mapStyle.setProperty("svg:fill", "#FFFFFF");
        mapStyle.setProperty("color-gradient", "none");
        styleSheet.addStyle(mapStyle, IStyleSheet.NORMAL_STYLES);
        styleMap.put("mapStyle", mapStyle);

        IStyle centralTopicStyle = styleSheet.createStyle(IStyle.TOPIC);
        centralTopicStyle.setProperty("line-width", "1pt");
        centralTopicStyle.setProperty("svg:fill", "#DCE6F2");
        centralTopicStyle.setProperty("fo:font-family", "Microsoft YaHei");
        centralTopicStyle.setProperty("border-line-width", "5pt");
        centralTopicStyle.setProperty("shape-class", "org.xmind.topicShape.roundedRect");
        centralTopicStyle.setProperty("fo:color", "#376092");
        centralTopicStyle.setProperty("line-class", "org.xmind.branchConnection.curve");
        centralTopicStyle.setProperty("border-line-color", "#558ED5");
        centralTopicStyle.setProperty("line-color", "#558ED5");
        styleSheet.addStyle(centralTopicStyle, IStyleSheet.NORMAL_STYLES);
        styleMap.put("centralTopicStyle", centralTopicStyle);

        IStyle mainTopicStyle = styleSheet.createStyle(IStyle.TOPIC);
        mainTopicStyle.setProperty("line-width", "1pt");
        mainTopicStyle.setProperty("svg:fill", "#DCE6F2");
        mainTopicStyle.setProperty("fo:font-family", "Microsoft YaHei");
        mainTopicStyle.setProperty("border-line-width", "2pt");
        mainTopicStyle.setProperty("shape-class", "org.xmind.topicShape.roundedRect");
        mainTopicStyle.setProperty("fo:color", "#17375E");
        mainTopicStyle.setProperty("line-class", "org.xmind.branchConnection.curve");
        mainTopicStyle.setProperty("border-line-color", "#558ED5");
        mainTopicStyle.setProperty("line-color", "#558ED5");
        styleSheet.addStyle(mainTopicStyle, IStyleSheet.NORMAL_STYLES);
        styleMap.put("mainTopicStyle", mainTopicStyle);

        IStyle subTopicStyle = styleSheet.createStyle(IStyle.TOPIC);
        subTopicStyle.setProperty("line-width", "1pt");
        subTopicStyle.setProperty("fo:font-family", "Microsoft YaHei");
        subTopicStyle.setProperty("border-line-width", "3pt");
        subTopicStyle.setProperty("line-class", "org.xmind.branchConnection.curve");
        subTopicStyle.setProperty("border-line-color", "#558ED5");
        subTopicStyle.setProperty("line-color", "#558ED5");
        styleSheet.addStyle(subTopicStyle, IStyleSheet.NORMAL_STYLES);
        styleMap.put("subTopicStyle", subTopicStyle);

        IStyle floatingTopicStyle = styleSheet.createStyle(IStyle.TOPIC);
        floatingTopicStyle.setProperty("svg:fill", "#558ED5");
        floatingTopicStyle.setProperty("fo:font-family", "Microsoft YaHei");
        floatingTopicStyle.setProperty("border-line-width", "0pt");
        floatingTopicStyle.setProperty("fo:color", "#FFFFFF");
        floatingTopicStyle.setProperty("fo:font-weight", "bold");
        floatingTopicStyle.setProperty("line-color", "#558ED5");
        styleSheet.addStyle(floatingTopicStyle, IStyleSheet.NORMAL_STYLES);
        styleMap.put("floatingTopicStyle", floatingTopicStyle);

        IStyle summaryTopic = styleSheet.createStyle(IStyle.TOPIC);
        summaryTopic.setProperty("fo:font-style", "italic");
        summaryTopic.setProperty("svg:fill", "#77933C");
        summaryTopic.setProperty("fo:font-family", "Microsoft YaHei");
        summaryTopic.setProperty("border-line-width", "0pt");
        summaryTopic.setProperty("fo:font-size", "10pt");
        summaryTopic.setProperty("shape-class", "org.xmind.topicShape.roundedRect");
        summaryTopic.setProperty("fo:color", "#FFFFFF");
        summaryTopic.setProperty("line-class", "org.xmind.branchConnection.curve");
        styleSheet.addStyle(summaryTopic, IStyleSheet.NORMAL_STYLES);
        styleMap.put("summaryTopic", floatingTopicStyle);

        IStyle itemTopic = styleSheet.createStyle(IStyle.TOPIC);
        itemTopic.setProperty("fo:text-align", "center");
        itemTopic.setProperty("line-width", "1pt");
        itemTopic.setProperty("svg:fill", "none");
        itemTopic.setProperty("fo:font-family", "Microsoft YaHei");
        itemTopic.setProperty("border-line-width", "2pt");
        itemTopic.setProperty("shape-class", "org.xmind.topicShape.underline");
        itemTopic.setProperty("fo:font-size", "14pt");
        itemTopic.setProperty("fo:color", "#17375E");
        itemTopic.setProperty("line-class", "org.xmind.branchConnection.curve");
        itemTopic.setProperty("border-line-color", "#558ED5");
        itemTopic.setProperty("line-color", "#558ED5");
        styleSheet.addStyle(itemTopic, IStyleSheet.NORMAL_STYLES);
        styleMap.put("itemTopic", itemTopic);

        return styleMap;
    }
}