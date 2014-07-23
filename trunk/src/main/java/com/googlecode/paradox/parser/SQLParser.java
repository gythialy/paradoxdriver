package com.googlecode.paradox.parser;

import java.io.IOException;
import java.nio.CharBuffer;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;

import com.googlecode.paradox.parser.nodes.FieldNode;
import com.googlecode.paradox.parser.nodes.SelectNode;
import com.googlecode.paradox.parser.nodes.StatementNode;
import com.googlecode.paradox.parser.nodes.TableNode;
import com.googlecode.paradox.utils.SQLStates;

public class SQLParser {

	private final String sql;
	private final Scanner scanner;

	public SQLParser(final String sql) {
		this.sql = sql;
		scanner = new Scanner(CharBuffer.wrap(sql.toCharArray()));
	}

	public ArrayList<StatementNode> parse() throws SQLException, IOException {
		final ArrayList<StatementNode> statementList = new ArrayList<StatementNode>();
		while (scanner.hasNext()) {
			final Token token = scanner.nextToken();

			switch (token.getType()) {
			case SEMI:
				if (statementList.size() == 0) {
					throw new SQLException("Unespected semicolon");
				}
				break;
			case SELECT:
				statementList.add(parseSelect());
				break;
			case INSERT:
				throw new SQLFeatureNotSupportedException("Not supported yet.", SQLStates.INVALID_SQL);
			case DELETE:
				throw new SQLFeatureNotSupportedException("Not supported yet.", SQLStates.INVALID_SQL);
			case UPDATE:
				throw new SQLFeatureNotSupportedException("Not supported yet.", SQLStates.INVALID_SQL);
			default:
				throw new SQLException("Invalid SQL: " + sql, SQLStates.INVALID_SQL);
			}
			return statementList;
		}
		throw new SQLException("Invalid SQL: " + sql, SQLStates.INVALID_SQL);
	}

	/**
	 * Parse a Select Statement
	 *
	 * @param parent
	 *            Statement Owner
	 * @return a Select Statement Node
	 * @throws SQLException
	 *             Invalid SQL
	 * @throws IOException
	 *             in case of parser exception
	 */
	private SelectNode parseSelect() throws SQLException, IOException {
		final SelectNode select = new SelectNode();
		Token t = scanner.nextToken();

		// Allowed only in the beginning of Select Statement
		if (t.getType() == TokenType.DISTINCT) {
			select.setDistinct(true);
		} else {
			scanner.pushBack(t);
		}

		// Field loop
		boolean firstField = true;
		while (scanner.hasNext()) {
			t = scanner.nextToken();

			if (t.getType() == TokenType.DISTINCT) {
				throw new SQLException("Invalid statement.");
			}

			if (t.getType() != TokenType.FROM) {
				// Field Name
				if (!firstField) {
					if (t.getType() != TokenType.COMMA) {
						throw new SQLException("Missing comma.", SQLStates.INVALID_SQL);
					}
					t = scanner.nextToken();
				}
				String tableName = null;
				String fieldName = t.getValue();
				String fieldAlias = fieldName;

				t = scanner.nextToken();
				if (t.getType() != TokenType.IDENTIFIER && t.getType() != TokenType.AS && t.getType() != TokenType.PERIOD) {
					scanner.pushBack(t);
				} else {
					// If it has a Table Name
					if (t.getType() == TokenType.PERIOD) {
						t = scanner.nextToken();
						tableName = fieldName;
						fieldName = t.getValue();
						fieldAlias = fieldName;
						if (!scanner.hasNext()) {
							throw new SQLException("Unexpected end of SELECT statement.", SQLStates.INVALID_SQL);
						}
						t = scanner.nextToken();
					}
					// Field alias (with AS identifier)
					if (t.getType() == TokenType.AS) {
						t = scanner.nextToken();
						fieldAlias = t.getValue();
					} else if (t.getType() == TokenType.IDENTIFIER) {
						// Field alias (without AS identifier)
						fieldAlias = t.getValue();
					}
				}

				select.getFields().add(new FieldNode(tableName, fieldName, fieldAlias));
				firstField = false;
			} else {
				break;
			}
		}

		if (t.getType() == TokenType.FROM) {
			firstField = true;
			while (scanner.hasNext()) {
				t = scanner.nextToken();

				if (!firstField) {
					if (t.getType() != TokenType.COMMA) {
						throw new SQLException("Missing comma.", SQLStates.INVALID_SQL);
					}
					t = scanner.nextToken();
				}
				if (t.getType() == TokenType.IDENTIFIER) {
					final String tableName = t.getValue();
					String tableAlias = tableName;

					if (scanner.hasNext()) {
						t = scanner.nextToken();
						if (t.getType() != TokenType.IDENTIFIER && t.getType() != TokenType.AS) {
							scanner.pushBack(t);
						} else {
							// Field alias (with AS identifier)
							if (t.getType() == TokenType.AS) {
								if (!scanner.hasNext()) {
									throw new SQLException("Unexpected end of SQL statement", SQLStates.INVALID_SQL);
								}
								t = scanner.nextToken();
								tableAlias = t.getValue();
							} else if (t.getType() == TokenType.IDENTIFIER) {
								// Field alias (without AS identifier)
								tableAlias = t.getValue();
							}
						}
					}
					select.getTables().add(new TableNode(tableName, tableAlias));
					firstField = false;
				} else if (t.getType() == TokenType.WHERE) {
					break;
				} else if (t != null) {
					throw new SQLException("Invalid SQL.", SQLStates.INVALID_SQL);
				}
			}
		} else {
			throw new SQLException("FROM expected.", SQLStates.INVALID_SQL);
		}
		return select;
	}
}