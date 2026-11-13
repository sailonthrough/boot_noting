package com.trip.noting.biz;

import com.alibaba.fastjson2.JSON;
import com.google.gson.JsonParser;
import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import lombok.Data;
import org.junit.Test;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
public class UtTest {

    public static class Detail {
        public String name;
        public String countryName;
    }

    //
    private String getDescription(Integer voyageType, Boolean isPort, List<Detail> poiDetails) {
        System.out.println("=========");
        System.out.println("voyageType" + voyageType);
        System.out.println("isPort" + isPort);
        System.out.println("poiDetails" + JSON.toJSONString(poiDetails));
        return "";
    }

    @Test
    public void getDescriptionTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        UtTest obj = new UtTest();
        Class<UtTest> utTestClass = UtTest.class;
        // 获取私有方法
        Method method = utTestClass.getDeclaredMethod("getDescription", Integer.class, Boolean.class, List.class);
        method.invoke(obj, 1, false, new ArrayList<Detail>() {{
            add(new Detail() {{
                this.name = "zou";
                this.countryName = "china";
            }});
        }});
    }

    public void printArray(int... array) {
        for (int number : array) {
            System.out.println(number);
        }
    }

    @Test
    public void testArrArgs() {
        LocalDateTime now = LocalDateTime.now();
        // LocalDateTime newNow = LocalDateTime.of(now.toLocalDate(), LocalTime.MIN);

        String format = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println(format);
        System.out.println(format.substring(11, 16));

    }

    @Test
    public void testMap() {
        List<String> strList = new ArrayList<>();
        strList.add("hello");
        strList.add("world");
        strList.clear();
        System.out.println(JSON.toJSONString(strList));
    }

    @Test
    public void testContains() {
        List<Long> strList = new ArrayList<>();
        boolean b = strList.stream().allMatch(x -> x.equals(33L));
        System.out.println(b);

    }

    @Data
    public static class AAA {
        private String aa;
        private String bb;
    }

    @Test
    public void testMonth() {
        AAA aaa = new AAA();
        aaa.aa="111";
        aaa.bb="222";
        System.out.println(aaa.toString());
    }

    @Test
    public void testImage() {
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println(format);
        System.out.println(System.currentTimeMillis() / 1000);
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(System.currentTimeMillis() / 1000, 0, ZoneOffset.ofHours(8));
        System.out.println(localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        long startTime = LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));
        System.out.println(startTime);
    }

    @Test
    public void testBool() {
        List<String> errorList = new ArrayList<>();

        String join = String.join("<br/>", errorList);
        System.out.println(join);

    }

    @Test
    public void testLarkSend() throws Exception {
        // 构建client
        Client client = Client.newBuilder("cli_a99b6ad9feb8d03c", "nCgF3Qcw3xUOzN05giWiog4IZiLZqEdc").build();

        UUID uuid = UUID.randomUUID();

        // 创建请求对象
        CreateMessageReq req = CreateMessageReq.newBuilder()
                // open_id,user_id,union_id,email,chat_id
                .receiveIdType("user_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId("TR037310")
                        .msgType("text")
                        .content("{\"text\":\"test content\"}")
                        .uuid(uuid.toString())
                        .build())
                .build();

        // 发起请求
        CreateMessageResp resp = client.im().v1().message().create(req);

        // 处理服务端错误
        if(!resp.success()) {
            System.out.println(String.format("code:%s,msg:%s,reqId:%s, resp:%s",
                    resp.getCode(), resp.getMsg(), resp.getRequestId(), Jsons.createGSON(true, false)
                            .toJson(JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8)))));
            return;
        }

        // 业务数据处理
        System.out.println(Jsons.DEFAULT.toJson(resp.getData()));
    }


    @Test
    public void testLark() throws Exception {
        // 构建client
        Client client = Client.newBuilder("cli_a99b6ad9feb8d03c", "nCgF3Qcw3xUOzN05giWiog4IZiLZqEdc").build();

        UUID uuid = UUID.randomUUID();

        // 创建请求对象
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType("chat_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        // oc_037e3983ffc0b78175113356a646e76d
                        .receiveId("oc_d15bb7a106ccecd54d171d831b66b5a6")
                        .msgType("text")
                        .content("{\"text\":\"lark message content\"}")
                        .uuid(uuid.toString())
                        .build())
                .build();

        // 发起请求
        CreateMessageResp resp = client.im().v1().message().create(req);

        // 处理服务端错误
        if(!resp.success()) {
            System.out.println(String.format("code:%s,msg:%s,reqId:%s, resp:%s",
                    resp.getCode(), resp.getMsg(), resp.getRequestId(), Jsons.createGSON(true, false)
                            .toJson(JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8)))));
            return;
        }

        // 业务数据处理
        System.out.println(Jsons.DEFAULT.toJson(resp.getData()));
    }

}
