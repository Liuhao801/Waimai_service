package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceServicel;

    /**
     * 获取从begin到end每天的日期
     * @param begin
     * @param end
     * @return
     */
    private List<LocalDate> getDateList(LocalDate begin,LocalDate end){
        //存放从begin到end每天的日期
        List<LocalDate> dateList =new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        return dateList;
    }

     /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //获取从begin到end每天的日期
        List<LocalDate> dateList = getDateList(begin, end);

        //存放每天的营业额
        List<Double>turnoverList=new ArrayList<>();
        for (LocalDate date : dateList) {
            //根据data查询当天营业额，营业额为指定时间内订单状态为已完成的金额总数
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map=new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover=orderMapper.sumByMap(map);
            turnover=turnover==null?0.0:turnover;  //将没有的营业额设为0
            turnoverList.add(turnover);
        }

        //封装VO返回结果
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //获取从begin到end每天的日期
        List<LocalDate> dateList = getDateList(begin, end);

        //每天的总用户数
        List<Integer> totalUserList=new ArrayList<>();
        //每天的新增用户数
        List<Integer> newUserList=new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map=new HashMap();
            map.put("end",endTime);
            //总用户数
            int totalUser=userMapper.countByMap(map);

            map.put("begin",beginTime);
            //新增用户数
            int newUser=userMapper.countByMap(map);

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }

        //封装VO返回结果
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();
    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //获取从begin到end每天的日期
        List<LocalDate> dateList = getDateList(begin, end);

        //每天的总订单数
        List<Integer> orderCountList=new ArrayList<>();
        //每天的已完成订单数
        List<Integer> validOrderCountList=new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map=new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            //总订单数
            int orderCount=orderMapper.countByMap(map);

            map.put("status",Orders.COMPLETED);
            //已完成订单数
            int validOrderCount=orderMapper.countByMap(map);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //计算时间区间内订单总数
        Integer totalOrderCount=orderCountList.stream().reduce(Integer::sum).get();
        //计算时间区间内已完成订单总数
        Integer validOrderCount=validOrderCountList.stream().reduce(Integer::sum).get();
        //计算时间区间订单完成率
        Double orderCompletionRate= totalOrderCount==0 ? 0.0 : validOrderCount.doubleValue()/totalOrderCount;

        //封装VO返回结果
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 查询销量排名top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10=orderMapper.getSalesTop10(beginTime,endTime);

        //获取菜品名称
        List<String> names=salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList=StringUtils.join(names,",");

        //获取菜品销量
        List<Integer> numbers=salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList=StringUtils.join(numbers,",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出Excel报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) {
        //查询数据库，获取近30天营业数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);

        //概览数据
        BusinessDataVO businessData = workspaceServicel.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MIN));

        //通过POI填充Excel文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        try {
            //根据模板创建一个新的excel文件
            XSSFWorkbook excel = new XSSFWorkbook(in);
            //获取文件标签页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            //获取第二行，填充时间数据
            sheet.getRow(1).getCell(1).setCellValue("时间："+dateBegin+"至"+dateEnd);

            //填充概览数据
            //获取第四行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());
            //获取第五行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());

            //填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = dateBegin.plusDays(i);
                BusinessDataVO data = workspaceServicel.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                row=sheet.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(data.getTurnover());
                row.getCell(3).setCellValue(data.getValidOrderCount());
                row.getCell(4).setCellValue(data.getOrderCompletionRate());
                row.getCell(5).setCellValue(data.getUnitPrice());
                row.getCell(6).setCellValue(data.getNewUsers());
            }

            //通过输出流将excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            //关闭资源
            out.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
