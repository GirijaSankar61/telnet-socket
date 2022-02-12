package com.example.savidemon.controller;

import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
public class DemoController
{
    public static final  String ENGINEVERSION = "engineversion:";
    private volatile     String _hostname     = "localhost";
    private static final int    PORT          = 4010;
    OutputStream outputStream = null;
    InputStream  inputStream  = null;
    private static final String HANDSHAKE_REQUEST = "SSSP/1.0";
    private static final String SCANDATA_REQUEST  = "SCANDATA";
    private static final String BYE_REQUEST       = "BYE";

    private static final String  HANDSHAKE_RESPONSE      = "OK SSSP/1.0";
    private static final String  ACKNOWLEDGE_RESPONSE    = "ACC";
    private static final String  QUERY_ENGINE_REQUEST    = "QUERY ENGINE";
    public static final  Charset ISO_8859_1_CHARSET      = Charsets.ISO_8859_1;
    private static final String  ISO_8859_1_CHARSET_NAME = ISO_8859_1_CHARSET.name();
    final                String  _lineTerminator         = "\r\n";
    private              String  engine_version          = null;
    Cache<String, String> cache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

    @GetMapping("/hello")
    public String hello()
        throws IOException, ExecutionException
    {

        final Socket clientSocket = new Socket(_hostname, PORT);
        outputStream = clientSocket.getOutputStream();
        inputStream = clientSocket.getInputStream();
        final BufferedReader lineReader =
            new BufferedReader(
                new InputStreamReader(
                    inputStream,
                    Charsets.ISO_8859_1
                )
            );

        handshake(lineReader, outputStream);
        log.info("Getting engine version <%s>", cache.get(ENGINEVERSION, () -> {
            log.info("Getting it from api call");
            return getEngineversion(lineReader, outputStream);
        }));

        sendCommand(BYE_REQUEST, outputStream);

        return "Hellow !!"+engine_version;

    }

    private void handshake(
        final BufferedReader lineReader,
        final OutputStream outputStream
    )
        throws IOException
    {
        Objects.requireNonNull(lineReader);
        Objects.requireNonNull(outputStream);

        receiveReply(SaviActionEnum.HANDSHAKE, lineReader);

        sendCommand(HANDSHAKE_REQUEST, outputStream);

        receiveReply(SaviActionEnum.ACKNOWLEDGE, lineReader);
    }

    private String getEngineversion(
        final BufferedReader lineReader,
        final OutputStream outputStream
    )
        throws IOException
    {
        Objects.requireNonNull(lineReader);
        Objects.requireNonNull(outputStream);

        sendCommand(QUERY_ENGINE_REQUEST, outputStream);
        receiveReply(SaviActionEnum.ENGINE_VERSION, lineReader);
        return engine_version;
    }

    void receiveReply(
        final SaviActionEnum action,
        final BufferedReader reader
    )
        throws IOException
    {
        Objects.requireNonNull(action);
        Objects.requireNonNull(reader);

        final String response = reader.readLine();
        log.info(response);

        switch (action)
        {
            case HANDSHAKE:
                if (!response.equalsIgnoreCase(HANDSHAKE_RESPONSE))
                {
                    log.error("Invalid HANDSHAKE response: <%s>", response);
                }
                break;
            case ACKNOWLEDGE:
                if (!response.startsWith(ACKNOWLEDGE_RESPONSE))
                {
                    log.error("Invalid ACK response: <%s>", response);
                }
                break;

            case ENGINE_VERSION:
                if (!response.startsWith(ACKNOWLEDGE_RESPONSE))
                {
                    log.error("Invalid ACK response: <%s>", response);
                }
                Optional<String> engineVersion = reader.lines()
                                                       .filter(p -> p.startsWith(ENGINEVERSION))
                                                       .map(p -> p.replace(ENGINEVERSION, "").trim())
                                                       .filter(q -> StringUtils.hasText(q))
                                                       .findAny();
                engine_version = engineVersion.orElse("");
                break;
            default:
                log.error("Did not understand action <%s> ---- <%s>", action, response);
        }
    }

    void sendCommand(
        final String cmd,
        final OutputStream outputStream
    )
        throws IOException
    {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(outputStream);

        outputStream.write(convertStringTo_ISO_8859_1_Bytes(cmd));

        writeLineTerminator(outputStream, _lineTerminator);

        outputStream.flush();

        log.debug("SophosAV: sent command <%s>", cmd);
    }

    enum SaviActionEnum
    {
        HANDSHAKE,
        ACKNOWLEDGE,
        ENGINE_VERSION
    }

    public static byte[] convertStringTo_ISO_8859_1_Bytes(final String source)
    {
        try
        {
            return source.getBytes(ISO_8859_1_CHARSET_NAME);
        }
        catch (final UnsupportedEncodingException ex)
        {
            log.error("error", new AssertionError(
                String.format(
                    "Unexpected decoding exception for valid charset <%s>",
                    ISO_8859_1_CHARSET_NAME
                ),
                ex
            ));
            ;
        }
        return null;
    }

    public static void writeLineTerminator(
        final OutputStream out,
        final String lineTerminator)
        throws IOException
    {
        Objects.requireNonNull(out);
        Objects.requireNonNull(lineTerminator);
        out.write(lineTerminator.getBytes());
    }
}
