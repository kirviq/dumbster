# dumbster
> The Dumbster is a very simple fake SMTP server designed for unit and system testing applications that
> send email messages. It responds to all standard SMTP commands but does not deliver messages to the user.
> The messages are stored within the Dumbster for later extraction and verification.

**This repository is a fork of [http://quintanasoft.com/dumbster/](http://quintanasoft.com/dumbster/).**

I forked it for two reasons:

1. I wanted to have typed lists as return values.
2. I wanted the server to be able to just pick a port itself and then tell me which port it got.

And while I'm at it, using slf4j instead of stdout is also nice.

Aside from that, the actual smtp logic is completely unchanged.

### Usage
Add maven dependency:
```xml
<dependency>
    <groupId>com.github.kirviq</groupId>
    <artifactId>dumbster</artifactId>
    <version>1.7.1</version>
    <scope>test</scope>
</dependency>
```
Start testing:
```java
class SomeTest {
    public void runTest() {
        try (SimpleSmtpServer dumbster = SimpleSmtpServer.start(SimpleSmtpServer.AUTO_SMTP_PORT)) {
        
            sendMessage(dumbster.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");
            
            List<SmtpMessage> emails = dumbster.getReceivedEmails();
            assertThat(emails, hasSize(1));
            SmtpMessage email = emails.get(0);
            assertThat(email.getHeaderValue("Subject"), is("Test"));
            assertThat(email.getBody(), is("Test Body"));
            assertThat(email.getHeaderValue("To"), is("receiver@there.com"));
        }
    }
}
```
See more examples in the included [unit tests](https://github.com/kirviq/dumbster/blob/master/src/test/java/com/dumbster/smtp/SimpleSmtpServerTest.java).

### Changelog

- 2018-02-06: Use ConcurrentLinkedQueue (FIFO) instead of ArrayList.
  This extends current functionality with the possibility to pop (i.e.
  get and remove) received messages.