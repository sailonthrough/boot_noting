package com.trip.noting;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.filter.NameFilter;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import lombok.Data;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.DigestUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BeanTest {

    static final String JOB_PACKAGE_NAME = BeanTest.class.getPackage().getName();
    // 共享的SimpleDateFormat实例（非线程安全）
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // 创建线程池（10个线程）
        ExecutorService executor = Executors.newFixedThreadPool(10);
        // 提交100个解析任务
        for (int i = 0; i < 100; i++) {
            final int day = (i % 30) + 1;  // 生成1-30的日期
            executor.submit(() -> {
                try {
                    // 构造日期字符串
                    String dateStr = "2023-05-" + String.format("%02d", day);

                    // 解析日期 - 这里会发生并发问题
                    Date date = sdf.parse(dateStr);

                    // 验证解析结果
                    String parsed = sdf.format(date);
                    if (!dateStr.equals(parsed)) {
                        System.err.println(Thread.currentThread().getName() + " | 解析错误: " + dateStr + " → " + parsed);
                    }
                } catch (Exception e) {
                    System.err.println(Thread.currentThread().getName() + " | 解析失败: " + e.getMessage());
                    // 打印堆栈中的关键信息
                    for (StackTraceElement ste : e.getStackTrace()) {
                        if (ste.getClassName().contains("NumberFormatException")) {
                            System.err.println("    at " + ste);
                        }
                    }
                }
            });
        }
        executor.shutdown();
    }

    private static void concurrency() throws ExecutionException, InterruptedException {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(3);
        numbers.add(4);
        numbers.add(2);
        List<CompletableFuture<Integer>> futures = numbers.stream().map(x -> CompletableFuture.supplyAsync(() -> divideNum(x)).handle((result, e) -> {
            if (e != null) {
                System.out.println("fail:" + x);
                System.out.println("fail" + e.getMessage());
                return 0;
            }
            return result;
        })).collect(Collectors.toList());
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.join();
        for (CompletableFuture<Integer> future : futures) {
            Integer i = future.get();
            System.out.println(i);
        }
    }

    private static int divideNum(Integer num) {
        Object object = new Object();
        object = null;
        Class<?> aClass = object.getClass();
        System.out.println(aClass);
        return num / (num - 2);

    }

    @Test
    public void beanCopyTest() {
        UserCopy userCopy = new UserCopy();
        userCopy.name = "zou";
        userCopy.age = 22;
        UserCopy newCopy = new UserCopy();
        System.out.println(JSON.toJSONString(newCopy));
        BeanUtils.copyProperties(userCopy, newCopy);
        System.out.println(JSON.toJSONString(newCopy));
    }

    @Test
    public void loaderCopyTest() throws IOException {
        String JOB_PACKAGE_NAME = BeanTest.class.getPackage().getName();
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ImmutableSet<ClassPath.ClassInfo> topLevelClasses = ClassPath.from(loader).getTopLevelClassesRecursive(JOB_PACKAGE_NAME);
        for (ClassPath.ClassInfo topLevelClass : topLevelClasses) {
            System.out.println(topLevelClass.getName());
        }
    }

    @Test
    public void testBuilder() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        TimeUnit.MILLISECONDS.sleep(2);
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        System.out.println(format);
    }

    @Test
    public void testG() throws InterruptedException {
        BigDecimal bigDecimal = new BigDecimal("2");
        int i = bigDecimal.compareTo(BigDecimal.ZERO);
        System.out.println(i);
    }

    @Test
    public void testRender() throws InterruptedException {
        List<User> users = new ArrayList<>();
        User user1 = new User();
        user1.setId(1);
        users.add(user1);
        User user3 = new User();
        user3.setId(3);
        users.add(user3);
        User user32 = new User();
        user32.setId(3);
        users.add(user32);
        User user5 = new User();
        user5.setId(5);
        users.add(user5);
        System.out.println(JSON.toJSONString(users));
        int theMaxDay = 5;
        Map<Integer, Boolean> addDayUserMap = new HashMap<>();
        addDayUserMap.put(2, true);
        addDayUserMap.put(4, true);
        int cruIndex = 0;
        int theDay = 1;
        while (users.size() - 1 > cruIndex) {
            while (!addDayUserMap.containsKey(theDay) && theDay < theMaxDay) {
                theDay++;
            }
            if (theDay == theMaxDay) {
                break;
            }
            User cruUser = users.get(cruIndex);
            User nextUser = users.get(cruIndex + 1);
            if (cruUser.getId() < theDay && nextUser.getId() > theDay) {
                User newUser = new User();
                newUser.setId(theDay);
                newUser.setName("new");
                users.add(cruIndex + 1, newUser);
                theDay++;
            }
            cruIndex++;
        }
        System.out.println(JSON.toJSONString(users));
    }

    @Test
    public void jsonTest() throws IOException {
        String s = "111.2086";
        String string = rectifyFloat(s);
        // rectifyFloat("3330200");
        System.out.println(string);

        String s2 = "111.20868";
        String string2 = rectifyFloat(s2);
        // rectifyFloat("3330200");
        System.out.println(string2);

    }

    // 矫正float类型精度,直接截取保留最多3位小数
    private String rectifyFloat(String floatStr) {
        if (StringUtils.isBlank(floatStr)) {
            return floatStr;
        }
        // 最多保留三位小数
        if (floatStr.length() <= 5) {
            return floatStr;
        }
        int index = floatStr.indexOf(".");
        if (index == -1) {
            return floatStr;
        }
        if (floatStr.length() - index - 1 >= 4) {
            return floatStr.substring(0, index + 4);
        }
        return floatStr;
    }

    @Test
    public void dateTest() throws IOException {
        String dateStr = "2020-09-08 09:09:09";
        String substring = dateStr.substring(0, 10);
        System.out.println(substring);
        System.out.println(dateStr.substring(0, 12));
    }

    @Test
    public void futureTest() throws IOException, ExecutionException, InterruptedException {
        String url = "http://ws.downloadfile.fx.ctripcorp.com/files/6/vacations/0F63512000fg4e84lA2AA.xlsx";
        String expiration = "2h";
        String rand = "123456";
        String auth = "727c487229e62f17c9f72c2a35688ed8";
        String str = url + expiration + rand + auth;
        System.out.println(str);
        String string = DigestUtils.md5DigestAsHex(str.getBytes(StandardCharsets.UTF_8));
        System.out.println(string);
    }

    @Test
    public void jsonParse() throws IOException {
        DefaultResourceLoader defaultResourceLoader = new DefaultResourceLoader();
        Resource resource = defaultResourceLoader.getResource("classpath:" + "temp.json");
        if (!resource.exists()) {
            throw new FileNotFoundException("File not found: " + "temp.json");
        }
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] buffer = new byte[inputStream.available()];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead == -1) {
                throw new IOException("Failed to read file: " + "temp.json");
            }
            String jsonContent = new String(buffer, StandardCharsets.UTF_8);
            List<SailingJson> sailings = JSON.parseArray(jsonContent, SailingJson.class);
            System.out.println(sailings.size());
            List<FlushJson> flushList = new ArrayList<>();
            for (SailingJson sailing : sailings) {
                flushList.add(new FlushJson() {{
                    setName(sailing.get_index());
                    setValue(sailing.get_id());
                }});
            }
            System.out.println(JSON.toJSONString(flushList, new UpperCaseNameFilter()));
        }
    }

    class UpperCaseNameFilter implements NameFilter {
        @Override
        public String process(Object object, String name, Object value) {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    @Test
    public void nullTest() throws IOException {
        FlushJson json = new FlushJson();
        if (json.getAge() == 9) {
            System.out.println(json.getAge());
        }
    }

    @Test
    public void compareTest() throws IOException {
        int i1 = new BigDecimal("2.3").compareTo(BigDecimal.ZERO);
        int i2 = new BigDecimal("-1").compareTo(BigDecimal.ZERO);
        int i3 = new BigDecimal("0").compareTo(BigDecimal.ZERO);
        System.out.println(i1);
        System.out.println(i2);
        System.out.println(i3);
    }

    public static byte[] compressData(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(data.getBytes());
        }
        return bos.toByteArray();
    }

    public static String decompressData(byte[] compressedData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData); GZIPInputStream gzipIS = new GZIPInputStream(bais)) {
            IOUtils.copy(gzipIS, baos);
        }
        return baos.toString();
    }

    @Test
    public void jsonSTest() throws IOException {
        String originalString = "这是一个12312 342423  4332423";
        String replacedString = originalString.replace(" ", "");
        System.out.println(replacedString);
    }

    public static String getRandomAlphanumeric(int length) {
        // 定义字母数字字符集合
        String alphanumericChars = "A0B1C2D3E4F5G6H7I8J9KLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder randomString = new StringBuilder(length);
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            // 从字母数字字符集合中随机选择一个字符
            int index = random.nextInt(alphanumericChars.length());
            randomString.append(alphanumericChars.charAt(index));
        }

        return randomString.toString();
    }

    private String renameFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        // 找到最后一个点号的位置
        int dotIndex = fileName.lastIndexOf('.');
        // 如果点号存在且不是文件名的第一个字符，返回点号后的字符串
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(0, dotIndex) + "_" + getRandomAlphanumeric(10) + fileName.substring(dotIndex);
        }
        // 如果没有找到点号或者点号是文件名的第一个字符，返回空字符串
        return "";
    }

    @Test
    public void testName() throws UnsupportedEncodingException {
        String name = "http://ws.downloadfile.fx.ctripcorp.com/files/6/vacations/0F63512000fg4e84lA2AA.xlsx2h123456727c487229e62f17c9f72c2a35688ed8";
        String string2 = Base64.getUrlEncoder().encodeToString(name.getBytes());
        System.out.println(string2);

    }

    private String renameFileName_(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        // 找到最后一个点号的位置
        int dotIndex = fileName.lastIndexOf('.');
        // 如果点号存在且不是文件名的第一个字符，返回点号后的字符串
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(0, dotIndex) + "_" + getRandomAlphanumeric(10) + fileName.substring(dotIndex);
        }
        // 如果没有找到点号或者点号是文件名的第一个字符，返回空字符串
        return "";
    }

    @Test
    public void testCode() {
        int code = 100000000 + 2001 * 1000;
        System.out.println(code / 100000000);
        int sharkcode = code % 100000000 / 1000;
        int sharktype = code / 100000000;
        System.out.println(sharkcode);
    }
    //   101001002

    @Test
    public void testCode1() {
        List<MapTest.User> users = new ArrayList<>();
        users.add(new MapTest.User() {{
            this.setId(1L);
            this.setName("1L");
        }});
        users.add(new MapTest.User() {{
            this.setId(3L);
            this.setName("1L");
        }});
        users.add(new MapTest.User() {{
            this.setId(2L);
            this.setName("1L");
        }});
        System.out.println(JSON.toJSONString(users));
        users.sort(Comparator.comparing(MapTest.User::getId));
        System.out.println(JSON.toJSONString(users));
        String jsonString = JSON.toJSONString(null);
        System.out.println(jsonString);
    }

    @Test
    public void testA() {
        int i = 100000000 + 2002 * 1000;
        System.out.println(i);
        // int code = 101001002;
        // int sharkcode = code % 100000000 / 1000;
        // int sharktype = code / 100000000;
        // System.out.println(sharkcode);
        // System.out.println(sharktype);

        int code = 101001018;
        int sharkcode = code % 100000000 / 1000;
        int sharktype = code / 100000000;
        System.out.println(sharkcode);
        System.out.println(sharktype);


    }

    @Data
    public static class RoomPriceDTO {
        public String esId;
        public Long roomId;
        public RoomData onlineData;
    }

    public static class RoomData {
        // 1 可超 0 不可超
        public Integer overSold;
        public Long totalInventory;
    }

    @Test
    public void testJSON() {
        RoomPriceDTO roomPriceDTO = new RoomPriceDTO();
        roomPriceDTO.setEsId("111");
        roomPriceDTO.setOnlineData(new RoomData() {{
            this.overSold = 1;
        }});
        System.out.println(JSON.toJSONString(roomPriceDTO));

    }

    @Test
    public void testVoyageName() {
        String input = "海达路德游轮·阿蒙森号8天4晚";
        Pattern pattern = Pattern.compile("\\d+天\\d+晚");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            System.out.println(matcher.group()); // 输出：17天16晚
        }

    }

    public static class RoomPrice {
        public Long mysql_id;
        public String ovary_room_id;
        public BigDecimal price;
        public String hive_d;
    }

    public static class RoomPriceNew {
        @JSONField(name = "mysql_id")
        public Long mysqlId;
        @JSONField(name = "ovary_room_id")
        public String ovaryRoomId;
        public BigDecimal price;
        @JSONField(name = "hive_d")
        public LocalDate date;
    }

    @Test
    public void testNUll() {
        List<RoomPrice> roomPrices = new ArrayList<>();
        roomPrices.add(new RoomPrice() {{
            this.mysql_id = 2L;
            this.ovary_room_id = "324";
            this.price = new BigDecimal("23.45");
            this.hive_d = "2025-05-16";
        }});
        String jsonString = JSON.toJSONString(roomPrices);
        System.out.println(jsonString);
        List<RoomPriceNew> roomPriceNews = JSON.parseArray(jsonString, RoomPriceNew.class);
        System.out.println(JSON.toJSONString(roomPriceNews));
    }

    @Test
    public void testStrLength() {
        for (int i = 1; i < 6; i = i + 2) {
            System.out.println(i);
            outer:
            for (int j = 2; true; ) {
                System.out.println(j);
                for (int k; true; k++) {
                    System.out.println("-----k------");
                    break outer;
                }
            }
        }
    }

    @Test
    public void testLength() {
        BigDecimal bigDecimal1 = new BigDecimal(Integer.MAX_VALUE);
        BigDecimal bigDecimal2 = new BigDecimal("123.45");
        System.out.println(bigDecimal1.compareTo(bigDecimal2));

    }


    @Test
    public void testSort() {
        List<MapTest.User> users = new ArrayList<>();
        users.add(new MapTest.User() {{
            this.setId(1);
            this.setAge(22);
        }});
        users.add(new MapTest.User() {{
            this.setId(2);
            this.setAge(22);
        }});
        users.add(new MapTest.User() {{
            this.setId(3);
            this.setAge(55);
        }});
        users.add(new MapTest.User() {{
            this.setId(4);
            this.setAge(55);
        }});
        System.out.println(JSON.toJSONString(users));
        users = users.stream().sorted(Comparator.comparing(MapTest.User::getAge)).collect(Collectors.toList());
        System.out.println(JSON.toJSONString(users));

    }

    @Test
    public void testReplace() {
        String str = "https://images4.c-ctrip.com/target/fd/cruise/g2/M01/7F/16/CghzgFWCjrCAIBEzAACtM9NNkEs748.jpg";
        int index = str.indexOf("/target/");
        System.out.println(index);
        System.out.println("https://dimg04.fx.ctripcorp.com" + str.substring(27));
    }

    @Test
    public void testReplace2() {
        String str = "携程从出行常识、旅游活动（风险性项目）和<A href=  特殊人群三方面为您提供旅游安全指南</A>，请您仔细阅读<A href=\"http://vacations.ctrip.com/notes/4939.html\"target=_blank>安全指南及警示</A>。";
        String replace1 = StringUtils.replace(str, "<A href", "<a href");
        String replace2 = StringUtils.replace(replace1, "</A>", "</a>");
        System.out.println(replace2);
    }

    @Test
    public void testLoop() {
        List<User> users = new ArrayList<>();
        users.add(new User() {{
            this.setId(1);
            this.setName("zou");
        }});
        System.out.println(JSON.toJSONString(users));
        User user = users.get(0);
        user.setName("zou----");
        System.out.println(JSON.toJSONString(user));
        System.out.println(JSON.toJSONString(users));
        List<User> newUsers = users.stream().filter(x -> x.id == 1).collect(Collectors.toList());
        User user1 = newUsers.get(0);
        System.out.println("user1" + JSON.toJSONString(user1));
        System.out.println(JSON.toJSONString(users));
    }

    public int lengthOfLongestSubstring(String s) {
        HashMap<Character, Integer> charIndex = new HashMap<>();
        int left = 0;
        int maxLen = 0;
        for (int right = 0; right < s.length(); right++) {
            char currentChar = s.charAt(right);
            // 如果字符已存在且在窗口内，更新左边界
            if (charIndex.containsKey(currentChar) && charIndex.get(currentChar) >= left) {
                left = charIndex.get(currentChar) + 1;
            }
            // 更新字符的最新位置
            charIndex.put(currentChar, right);
            // 计算当前窗口长度
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }

    @Test
    public void length_() {
        List<MapTest.User> users = new ArrayList<>();
        users.add(new MapTest.User() {{
            this.setName("Hello1");
            this.setId(1);
        }});
        users.add(new MapTest.User() {{
            this.setName("Hello2");
            this.setId(2);
        }});
        users.add(new MapTest.User() {{
            this.setName("Hello3");
            this.setId(3);
        }});
        System.out.println(JSON.toJSONString(users));
        List<MapTest.User> users2 = new ArrayList<>();
        users2.add(new MapTest.User() {{
            this.setName("Hello4");
            this.setId(4);
        }});
        users2.add(new MapTest.User() {{
            this.setName("Hello5");
            this.setId(5);
        }});
        users2.add(new MapTest.User() {{
            this.setName("Hello6");
            this.setId(6);
        }});
        users.addAll(users2);
        System.out.println(JSON.toJSONString(users));
    }

    @Data
    public static class User {
        private long id;
        // 1 有效 0 无效
        private int active;
        private String name;
        // 1 待送审 2 审核中 3 审核通过 4 审核驳回
        private int status;
        // 价格
        private BigDecimal price;
    }

    @Test
    public void sort() {
        // 排序方案：审核驳回>审核中>待送审>审核通过>有效>无效> 价格（升序）
        List<User> users = new ArrayList<>();
        users.add(new User() {{
            this.setId(1);
            this.setName("A");
            this.setStatus(1);
            this.setActive(1);
            this.setPrice(new BigDecimal(1));
        }});
        users.add(new User() {{
            this.setId(2);
            this.setName("B");
            this.setStatus(2);
            this.setPrice(new BigDecimal(4));
        }});
        users.add(new User() {{
            this.setId(3);
            this.setName("C");
            this.setStatus(4);
            this.setActive(1);
            this.setPrice(new BigDecimal(10));
        }});
        users.add(new User() {{
            this.setId(4);
            this.setName("D");
            this.setStatus(4);
            this.setActive(1);
            this.setPrice(new BigDecimal(8));
        }});
        System.out.println(JSON.toJSONString(users));

        // 定义排序规则
        users.sort(Comparator.comparingInt((User u) -> {
                            // 处理status排序优先级
                            switch (u.getStatus()) {
                                case 4:
                                    return 1;  // 审核驳回 最高优先级
                                case 2:
                                    return 2;  // 审核中
                                case 1:
                                    return 3;  // 待送审
                                case 3:
                                    return 4;  // 审核通过
                                default:
                                    return Integer.MAX_VALUE;
                            }
                        }).thenComparingInt(u -> u.getActive() == 1 ? 0 : 1) // 有效优先
                        .thenComparing(User::getPrice) // 价格升序
        );
        System.out.println(JSON.toJSONString(users));

    }

    @Test
    public void second() throws ParseException {
        String passingThroughTheCity = " - 上海 - 宁波 - 宁波 - 温州 - 海上巡游 - 上海";
        passingThroughTheCity = passingThroughTheCity.substring(3);
        System.out.println(passingThroughTheCity);
    }

    public static class DateRange {
        public Date sailingStartDateFrom;
        public Date sailingStartDateTo;
    }

    @Test
    public void dateRangeTest() throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date now = Date.from(Instant.now());
        String nowFormat = simpleDateFormat.format(now);
        System.out.println(nowFormat);
        Date parse1900 = simpleDateFormat.parse("1900-01-01");
        String format1900 = simpleDateFormat.format(parse1900);
        System.out.println(format1900);

        List<DateRange> dateRanges = new ArrayList<>();
        dateRanges.add(new DateRange() {{
            this.sailingStartDateFrom = parse1900;
            this.sailingStartDateTo = parse1900;
        }});
        System.out.println(JSON.toJSONString(dateRanges));

        dateRanges = dateRanges.stream().filter(x -> x.sailingStartDateFrom.compareTo(now) <= 0 || (x.sailingStartDateFrom.compareTo(now) <= 0 && x.sailingStartDateTo.compareTo(now) >= 0)).collect(Collectors.toList());
        System.out.println(JSON.toJSONString(dateRanges));


    }

    @Test
    public void sort1() throws ParseException {
        String str = "%2$s%1$s";
        String format = String.format(str, "中国", "上海");
        System.out.println(format);
    }

    @Test
    public void sort2() throws ParseException, InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("task1");
        TimeUnit.MILLISECONDS.sleep(200);
        stopWatch.stop();
        stopWatch.start("task2");
        TimeUnit.MILLISECONDS.sleep(150);
        stopWatch.stop();
        StopWatch.TaskInfo[] taskInfo = stopWatch.getTaskInfo();
        for (StopWatch.TaskInfo info : taskInfo) {
            String taskName = info.getTaskName();
            long timeMillis = info.getTimeMillis();
            System.out.println("taskName:" + taskName + ", timeMillis:" + timeMillis);
        }
        long totalTimeMillis = stopWatch.getTotalTimeMillis();
        System.out.println(totalTimeMillis);
    }

    @Test
    public void itemSort() throws ParseException, InterruptedException {
        String[] testStrings = {
                "Hello %s",                // 包含 %s
                "Value: %d",               // 包含 %d
                "Number %1$s",             // 包含 %1$s
                "Percent 100%%",           // 转义的百分号，不是占位符
                "No placeholder here",     // 没有占位符
                "Mixed %2$f and %s"        // 包含多个
        };

        for (String str : testStrings) {
            boolean has = hasPlaceholder(str);
            System.out.println("\"" + str + "\" -> " + has);
        }
    }

    private boolean hasPlaceholder(String input) {
        // 正则解释：
        // %                    字面百分号
        // (?:\\d+\\$)?         可选：数字 + $（如 1$）
        // [a-zA-Z%]            转换符：字母 或 %（表示转义的百分号）
        // 注意：%% 也会被匹配，如果想排除它，可以改进正则，但通常简单匹配就够了
        String regex = "%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtTn]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    @Test
    public void hashTest() throws NoSuchAlgorithmException {
        String input = "";
        // 1. 获取 SHA-256 MessageDigest
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // 2. 计算 hash（得到 256-bit = 32-byte 的结果）
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        // 3. 转为十六进制
        String hex = bytesToHex(hashBytes);
        System.out.println("SHA-256 Hex:");
        System.out.println(hex);

        // 4. 转为 Base64
        String base64 = Base64.getEncoder().encodeToString(hashBytes);
        System.out.println("SHA-256 Base64:");
        System.out.println(base64);
    }

    // byte[] -> hex string
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    public void testTrue(){
        if (1 > 2 && (true) || (true)) {
            System.out.println("aaaaaa");
        }
    }


}
