package br.commons.http;

import br.commons.Result;
import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * HTTP response status. The framework only carries this object — the number appears only at the edge
 * ({@code sendResponseHeaders}, error JSON body). Constants cover the IANA registry with RFC 9110 names;
 * a code outside it goes through {@link #custom}, the only way out of this class — the constructor is
 * private so no status is created without passing range validation.
 */
@NullMarked
public final class HTTPStatus {

    private final int code;
    private final String name;
    private final String description;

    private HTTPStatus(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    // 1xx — informational
    public static final HTTPStatus CONTINUE = new HTTPStatus(100, "Continue", "O cliente pode continuar enviando o corpo da requisição");
    public static final HTTPStatus SWITCHING_PROTOCOLS = new HTTPStatus(101, "Switching Protocols", "O servidor aceitou trocar de protocolo");
    public static final HTTPStatus PROCESSING = new HTTPStatus(102, "Processing", "Requisição recebida, ainda em processamento");
    public static final HTTPStatus EARLY_HINTS = new HTTPStatus(103, "Early Hints", "Cabeçalhos antecipados antes da resposta final");

    // 2xx — success
    public static final HTTPStatus OK = new HTTPStatus(200, "OK", "Requisição atendida");
    public static final HTTPStatus CREATED = new HTTPStatus(201, "Created", "Recurso criado");
    public static final HTTPStatus ACCEPTED = new HTTPStatus(202, "Accepted", "Requisição aceita para processamento posterior");
    public static final HTTPStatus NON_AUTHORITATIVE_INFORMATION = new HTTPStatus(203, "Non-Authoritative Information", "Resposta modificada por um intermediário");
    public static final HTTPStatus NO_CONTENT = new HTTPStatus(204, "No Content", "Requisição atendida, sem corpo de resposta");
    public static final HTTPStatus RESET_CONTENT = new HTTPStatus(205, "Reset Content", "O cliente deve reiniciar a visão do documento");
    public static final HTTPStatus PARTIAL_CONTENT = new HTTPStatus(206, "Partial Content", "Apenas a faixa solicitada do recurso");
    public static final HTTPStatus MULTI_STATUS = new HTTPStatus(207, "Multi-Status", "Vários status para operações independentes");
    public static final HTTPStatus ALREADY_REPORTED = new HTTPStatus(208, "Already Reported", "Membros já listados em resposta anterior");
    public static final HTTPStatus IM_USED = new HTTPStatus(226, "IM Used", "Resposta com manipulações de instância aplicadas");

    // 3xx — redirects
    public static final HTTPStatus MULTIPLE_CHOICES = new HTTPStatus(300, "Multiple Choices", "Há mais de uma representação disponível");
    public static final HTTPStatus MOVED_PERMANENTLY = new HTTPStatus(301, "Moved Permanently", "Recurso movido de forma permanente");
    public static final HTTPStatus FOUND = new HTTPStatus(302, "Found", "Recurso temporariamente em outro endereço");
    public static final HTTPStatus SEE_OTHER = new HTTPStatus(303, "See Other", "Resultado disponível em outro endereço, via GET");
    public static final HTTPStatus NOT_MODIFIED = new HTTPStatus(304, "Not Modified", "A cópia em cache do cliente continua válida");
    public static final HTTPStatus USE_PROXY = new HTTPStatus(305, "Use Proxy", "Obsoleto: o recurso deve ser acessado via proxy");
    public static final HTTPStatus TEMPORARY_REDIRECT = new HTTPStatus(307, "Temporary Redirect", "Redirecionamento temporário mantendo o método");
    public static final HTTPStatus PERMANENT_REDIRECT = new HTTPStatus(308, "Permanent Redirect", "Redirecionamento permanente mantendo o método");

    // 4xx — client errors
    public static final HTTPStatus BAD_REQUEST = new HTTPStatus(400, "Bad Request", "Requisição malformada");
    public static final HTTPStatus UNAUTHORIZED = new HTTPStatus(401, "Unauthorized", "Autenticação ausente ou inválida");
    public static final HTTPStatus PAYMENT_REQUIRED = new HTTPStatus(402, "Payment Required", "Reservado para uso futuro");
    public static final HTTPStatus FORBIDDEN = new HTTPStatus(403, "Forbidden", "Autenticado, mas sem permissão");
    public static final HTTPStatus NOT_FOUND = new HTTPStatus(404, "Not Found", "Recurso não encontrado");
    public static final HTTPStatus METHOD_NOT_ALLOWED = new HTTPStatus(405, "Method Not Allowed", "Método não suportado pelo recurso");
    public static final HTTPStatus NOT_ACCEPTABLE = new HTTPStatus(406, "Not Acceptable", "Nenhuma representação atende ao Accept");
    public static final HTTPStatus PROXY_AUTHENTICATION_REQUIRED = new HTTPStatus(407, "Proxy Authentication Required", "O proxy exige autenticação");
    public static final HTTPStatus REQUEST_TIMEOUT = new HTTPStatus(408, "Request Timeout", "O cliente demorou demais para enviar a requisição");
    public static final HTTPStatus CONFLICT = new HTTPStatus(409, "Conflict", "Conflito com o estado atual do recurso");
    public static final HTTPStatus GONE = new HTTPStatus(410, "Gone", "Recurso removido de forma permanente");
    public static final HTTPStatus LENGTH_REQUIRED = new HTTPStatus(411, "Length Required", "Content-Length obrigatório");
    public static final HTTPStatus PRECONDITION_FAILED = new HTTPStatus(412, "Precondition Failed", "Pré-condição dos cabeçalhos não atendida");
    public static final HTTPStatus CONTENT_TOO_LARGE = new HTTPStatus(413, "Content Too Large", "Corpo da requisição grande demais");
    public static final HTTPStatus URI_TOO_LONG = new HTTPStatus(414, "URI Too Long", "URI longa demais");
    public static final HTTPStatus UNSUPPORTED_MEDIA_TYPE = new HTTPStatus(415, "Unsupported Media Type", "Formato do corpo não suportado");
    public static final HTTPStatus RANGE_NOT_SATISFIABLE = new HTTPStatus(416, "Range Not Satisfiable", "Faixa solicitada fora do recurso");
    public static final HTTPStatus EXPECTATION_FAILED = new HTTPStatus(417, "Expectation Failed", "Cabeçalho Expect não atendido");
    public static final HTTPStatus IM_A_TEAPOT = new HTTPStatus(418, "I'm a teapot", "RFC 2324 — o servidor é um bule");
    public static final HTTPStatus MISDIRECTED_REQUEST = new HTTPStatus(421, "Misdirected Request", "Requisição enviada ao servidor errado");
    public static final HTTPStatus UNPROCESSABLE_CONTENT = new HTTPStatus(422, "Unprocessable Content", "Corpo bem formado, mas semanticamente inválido");
    public static final HTTPStatus LOCKED = new HTTPStatus(423, "Locked", "Recurso bloqueado");
    public static final HTTPStatus FAILED_DEPENDENCY = new HTTPStatus(424, "Failed Dependency", "Falhou porque uma operação anterior falhou");
    public static final HTTPStatus TOO_EARLY = new HTTPStatus(425, "Too Early", "O servidor ainda não está pronto para processar");
    public static final HTTPStatus UPGRADE_REQUIRED = new HTTPStatus(426, "Upgrade Required", "O cliente precisa trocar de protocolo");
    public static final HTTPStatus PRECONDITION_REQUIRED = new HTTPStatus(428, "Precondition Required", "A requisição precisa ser condicional");
    public static final HTTPStatus TOO_MANY_REQUESTS = new HTTPStatus(429, "Too Many Requests", "Limite de requisições excedido");
    public static final HTTPStatus REQUEST_HEADER_FIELDS_TOO_LARGE = new HTTPStatus(431, "Request Header Fields Too Large", "Cabeçalhos grandes demais");
    public static final HTTPStatus UNAVAILABLE_FOR_LEGAL_REASONS = new HTTPStatus(451, "Unavailable For Legal Reasons", "Indisponível por determinação legal");

    // 5xx — server errors
    public static final HTTPStatus INTERNAL_SERVER_ERROR = new HTTPStatus(500, "Internal Server Error", "Erro interno do servidor");
    public static final HTTPStatus NOT_IMPLEMENTED = new HTTPStatus(501, "Not Implemented", "Funcionalidade não suportada pelo servidor");
    public static final HTTPStatus BAD_GATEWAY = new HTTPStatus(502, "Bad Gateway", "Resposta inválida do servidor de origem");
    public static final HTTPStatus SERVICE_UNAVAILABLE = new HTTPStatus(503, "Service Unavailable", "Servidor temporariamente indisponível");
    public static final HTTPStatus GATEWAY_TIMEOUT = new HTTPStatus(504, "Gateway Timeout", "O servidor de origem não respondeu a tempo");
    public static final HTTPStatus HTTP_VERSION_NOT_SUPPORTED = new HTTPStatus(505, "HTTP Version Not Supported", "Versão do HTTP não suportada");
    public static final HTTPStatus VARIANT_ALSO_NEGOTIATES = new HTTPStatus(506, "Variant Also Negotiates", "Erro de configuração na negociação de conteúdo");
    public static final HTTPStatus INSUFFICIENT_STORAGE = new HTTPStatus(507, "Insufficient Storage", "Sem espaço para concluir a operação");
    public static final HTTPStatus LOOP_DETECTED = new HTTPStatus(508, "Loop Detected", "Laço infinito detectado no processamento");
    public static final HTTPStatus NOT_EXTENDED = new HTTPStatus(510, "Not Extended", "Extensões adicionais exigidas");
    public static final HTTPStatus NETWORK_AUTHENTICATION_REQUIRED = new HTTPStatus(511, "Network Authentication Required", "A rede exige autenticação");

    /** Status outside the registry — the code must be in the range that the protocol accepts (100–599). */
    @CheckReturnValue
    public static Result<HTTPStatus, String> custom(int code, String name, String description) {
        if (code < 100 || code > 599) {
            return Result.failure("HTTP status fora da faixa 100–599: " + code);
        }
        return Result.success(new HTTPStatus(code, name, description));
    }

    public int code() { return code; }
    public String name() { return name; }
    public String description() { return description; }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof HTTPStatus status
                && code == status.code
                && name.equals(status.name)
                && description.equals(status.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, description);
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
