这是基于Spring WebFlux造的文件上传下载轮子，有多快不知道（说不准还慢了）。但是！能经受的住更多的连接请求是真的。毕竟基于Netty作为服务器。

其实我之前写过纯Netty的，速度比Spring Servlet快一些，但是当时Netty源码还没看，线程模型不清楚，不是很了解怎么进行耗时任务处理（所以直接使用附加Executor处理的）

为了稳定，这次换了WebFlux。

直接说用法：


- 上传文件，method=POST, url=/upload/{uuid}。其中uuid是事先在内存设置好的。每个属性之间使用';'分隔开。其中filename可为空，为空即使用原本文件名；direname指出文件保存目录，不可为空；iscover指出是否覆盖同名文件，不可为空。格式如下：
```
filename=[文件名];direname=[目录名];iscover=[true/false]
```


- 多文件上传，设置属性时使用'&'分隔开，格式如下：
```
filename=[文件名];direname=[目录名];iscover=[true/false]&filename=[第二个文件的名称];...
```


- 下载文件，method-GET, url=/download/{uuid}。其中uuid是事先在内存设置好的。此属性不可为空，否则会报错。格式如下：
```
[文件路径]。比如/Users/joker/Desktop/test/123.docx
```


- 删除文件，method=DELETE, url=/delete/{uuid}。其中uuid是事先在内存设置好的。格式如下：
```
[文件路径]。比如/Users/joker/Desktop/test/123.docx
```
