package io.zrz.jpgsql.client;

import java.time.Duration;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PostgresConnectionProperties {

  public static enum SslMode {
    Disable,
    Require,
    VerifyCA,
    VerifyFull
  }

  /**
   * The hostname. Localhost by default.
   */

  private String hostname;

  /**
   * The port to connect to. 5432 by default.
   */

  private int port;

  /**
   * The database name to connect to.
   */

  private String dbname;

  /**
   * The username to use for connecting.
   */

  private String username;

  /**
   * The password to use for connecting.
   */

  private String password;

  /**
   * The minimum number of idle connections.
   */

  @Default
  private int minIdle = 0;

  /**
   * how long a connection is idle before it is closed and removed from the pool.
   */

  @Default
  private Duration idleTimeout = Duration.ofSeconds(60);

  /**
   * how long a connection tries to establish before timing out.
   */

  @Default
  private Duration connectTimeout = Duration.ofSeconds(10);

  /**
   * maximum number of connections.
   *
   * be aware that if the {@link org.postgresql} version of the PostgresClient is used and configured to use a thread
   * for async emulation, then this will be equal to the number of threads created (urgh).
   *
   */

  @Default
  private int maxPoolSize = 10;

  /**
   * how long a pending query can wait for a connection to be available when it seems like progress has been stalled.
   * 
   * this happens when no connections are available, and we're trying to connect.
   * 
   * if set to zero, enqueing will be rejected and all enqueued queries will be rejected when there is no connection
   * available, with the exception of the first startup - which uses the connectTimeout instead.
   * 
   */

  @Default
  private Duration maxStalledWait = Duration.ofSeconds(2);

  /**
   * The number of queries that can be queued for execution.
   *
   * If set to zero, this will not allow more than the {@link #getMaxPoolSize()} number of pending/executing queries.
   *
   */

  private int queueDepth;

  /**
   * if this connection is read only?
   */

  @Default
  private boolean readOnly = false;

  @Default
  private boolean ssl = false;

  @Default
  private String sslMode = null;

  @Default
  private Duration socketTimeout = null;

  @Default
  private String applicationName = "jpgsql";

  // 0 == no batching
  @Default
  private int defaultRowFetchSize = 0;

  @Default
  private int sendBufferSize = 1024 * 64;

  @Default
  private int recvBufferSize = 1024 * 64;

  @Default
  private boolean debug = false;

  @Default
  private String binaryTransferDisable = null;

}
