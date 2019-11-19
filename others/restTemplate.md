# 参考链接
* [restTemplate整体操作](https://blog.csdn.net/itguangit/article/details/78825505)
* [post使用实例](https://segmentfault.com/a/1190000007778403)
* [什么是restful](https://blog.csdn.net/itguangit/article/details/80198895)

# getForEntity,getForObject,postForEntity,postForObject的区别
entity进行了封装将请求返回的状态码，返回头信息进行了封装，object相当于entity.getBody()，只有返回信息的消息体

# get样例

## 无参的getForEntity
ResponseEntity<返回值类型> responseEntity = restTemplate.getForEntity("url", 返回值类型.class);

## 有参的getForEntity

* 使用{}进行url路径占位符
ResponseEntity<返回值类型> responseEntity = restTemplate.getForEntity("http://localhost/get/{id}", 返回值类型.class, id)

* 使用map封装参数

//封装参数，千万不要替换为Map与HashMap，否则参数无法传递
MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
map.put("id",id);

ResponseEntity<返回值类型> responseEntity = restTemplate.getForEntity("url", 返回值类型.class, map);

# post样例
* 请求携带cookie

```java
HttpHeaders headers = new HttpHeaders();
List<String> cookies = new ArrayList<>();
cookies.add("JSESSIONID=" + Strings.nullToEmpty(jsessionId));
cookies.add("token=" + Strings.nullToEmpty(token));
headers.put(HttpHeaders.COOKIE,cookies);
HttpEntity request = new HttpEntity(null, headers);
ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
```

* post表单

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//封装参数，千万不要替换为Map与HashMap，否则参数无法传递
MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
map.add("title", title);
map.add("desc", desc);
map.add("userid", toUserId);
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);
ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
```

* post json

```java
HttpHeaders headers = new HttpHeaders();

headers.setContentType(MediaType.APPLICATION_JSON);
//有时候要设置编码，否则会报apiToken参数错误
headers.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));

headers.setAccept(Lists.newArrayList(MediaType.APPLICATION_JSON));
HttpEntity<String> entity = new HttpEntity<String>(requestJson, headers);
ResponseEntity<String> resp = restTemplate.postForEntity(url,entity,String.class);
```

* url post

```java
String template = baseUrl + "/demo?app={0}&userId={1}";
String url = MessageFormat.format(template,app,userId);
return restTemplate.postForEntity(url,null,String.class);
```

# exchange方法
exchange与get和post接口不同，exchange方法和postForEntity类似，但是更灵活，exchange还可以调用get请求
* exchange允许调用者指定HTTP请求的方法（GET,POST,PUT等）
* exchange可以在请求中增加body以及头信息，其内容通过参数 HttpEntity<?>requestEntity 描述
* exchange支持‘含参数的类型’（即泛型类）作为返回类型，该特性通过 ParameterizedTypeReferenceresponseType 描述

```java
HttpHeaders headers = new HttpHeaders();
headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
//没有参数这样写
HttpEntity<String> entity = new HttpEntity<String>(headers);
//有参数这样写
MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
map.add("title", title);
map.add("desc", desc);
map.add("userid", toUserId);
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);
ResponseEntity<byte[]> response = restTemplate.exchange(url,HttpMethod.GET, entity, byte[].class);
byte[] imageBytes = response.getBody();
```

