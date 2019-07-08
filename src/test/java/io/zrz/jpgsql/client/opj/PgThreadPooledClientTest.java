package io.zrz.jpgsql.client.opj;

import org.junit.Ignore;
import org.junit.Test;

public class PgThreadPooledClientTest {

  @Ignore
  @Test
  public void test() throws InterruptedException {
    final PgThreadPooledClient client = PgThreadPooledClient.create("localhost", "saasy");
    //final Flowable<NotifyMessage> notifies = client.notifications(Sets.newHashSet("xxx"));
    //notifies.blockingSubscribe(notify -> {
    //  System.err.println(notify);
    //});
  }

}
