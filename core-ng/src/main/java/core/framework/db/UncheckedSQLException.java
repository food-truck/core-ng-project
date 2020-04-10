package core.framework.db;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * @author neo
 */
public final class UncheckedSQLException extends RuntimeException {
    private static final long serialVersionUID = 5857178985477320780L;

    public final ErrorType errorType;

    // expose original sqlState to support handle errors not covered by errorType
    public final String sqlSate;

    public UncheckedSQLException(SQLException e) {
        super(e.getMessage() + ", sqlState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode(), e);
        sqlSate = e.getSQLState();
        errorType = errorType(e);
    }

    // different jdbc driver translates sqlState to exception differently, but with common set
    // hsqldb: org.hsqldb.jdbc.JDBCUtil,
    // mysql: com.mysql.cj.jdbc.exceptions.SQLError
    private ErrorType errorType(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) return ErrorType.INTEGRITY_CONSTRAINT_VIOLATION;
        String state = e.getSQLState();
        if (state != null && state.startsWith("08")) return ErrorType.CONNECTION_ERROR;
        return null;
    }

    // currently only valid use case to catch UncheckedSQLException is for duplicate key / constraint violation , e.g. register with duplicate name,
    // it should not catch UncheckedSQLException for rest use cases
    public enum ErrorType {
        CONNECTION_ERROR,   // error type used to retry db operation potentially for top critical system, in cloud env / webapp, there are only limited use cases where retry db query makes sense
        INTEGRITY_CONSTRAINT_VIOLATION
    }
}
