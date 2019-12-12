/**
 * Copyright (c) 2018-present, http://a2-solutions.eu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package eu.solutions.a2.cdc.oracle;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Timestamp;

import eu.solutions.a2.cdc.oracle.standalone.avro.AvroSchema;

public class OraColumn {

	public static final String ROWID_KEY = "ORA$ROW$ID";
	public static final String MVLOG_SEQUENCE = "SEQUENCE$$";

	private final static float LOG_2 = 0.30103f; 

	private final String columnName;
	private final boolean partOfPk;
	private final int jdbcType;
	private final boolean nullable;
	private AvroSchema avroSchema;
	private boolean oracleDate = false;
	private int dataScale = 0;

	/**
	 * 
	 * Construct column definition from Resultset (ORA2JSON) standalone
	 * 
	 * @param resultSet
	 * @throws SQLException
	 */
	public OraColumn(final ResultSet resultSet) throws SQLException {
		this.columnName = resultSet.getString("COLUMN_NAME");
		final String partOfPkString = resultSet.getString("PK");
		if (!resultSet.wasNull() && "Y".equals(partOfPkString))
			this.partOfPk = true;
		else
			this.partOfPk = false;
		this.nullable = "Y".equals(resultSet.getString("NULLABLE")) ? true : false;
		final String oraType = resultSet.getString("DATA_TYPE");
		switch (oraType) {
			case "DATE":
				jdbcType = Types.DATE;
				if (this.nullable)
					this.avroSchema = AvroSchema.DATE_OPTIONAL();
				else
					this.avroSchema = AvroSchema.DATE_MANDATORY();
				break;
			case "FLOAT":
				jdbcType = Types.FLOAT;
				if (this.nullable)
					this.avroSchema = AvroSchema.FLOAT64_OPTIONAL();
				else
					this.avroSchema = AvroSchema.FLOAT64_MANDATORY();
				break;
			case "NUMBER":
				final int dataPrecision = resultSet.getInt("DATA_PRECISION"); 
				dataScale = resultSet.getInt("DATA_SCALE");
				if (dataScale == 0) {
					if (dataPrecision == 0) {
						// Just NUMBER.....
						// OEBS and other legacy systems specific
						// Can be Integer or decimal or float....
						jdbcType = Types.DOUBLE;
						if (this.nullable)
							this.avroSchema = AvroSchema.FLOAT64_OPTIONAL();
						else
							this.avroSchema = AvroSchema.FLOAT64_MANDATORY();
					}
					else if (dataPrecision < 3) {
						jdbcType = Types.TINYINT;
						if (this.nullable)
							this.avroSchema = AvroSchema.INT8_OPTIONAL();
						else
							this.avroSchema = AvroSchema.INT8_MANDATORY();
					}
					else if (dataPrecision < 5) {
						jdbcType = Types.SMALLINT;
						if (this.nullable)
							this.avroSchema = AvroSchema.INT16_OPTIONAL();
						else
							this.avroSchema = AvroSchema.INT16_MANDATORY();
					}
					else if (dataPrecision < 10) {
						jdbcType = Types.INTEGER;
						if (this.nullable)
							this.avroSchema = AvroSchema.INT32_OPTIONAL();
						else
							this.avroSchema = AvroSchema.INT32_MANDATORY();
					}
					else {
						jdbcType = Types.BIGINT;
						if (this.nullable)
							this.avroSchema = AvroSchema.INT64_OPTIONAL();
						else
							this.avroSchema = AvroSchema.INT64_MANDATORY();
					}
				} else {
					jdbcType = Types.DOUBLE;
					if (this.nullable)
						this.avroSchema = AvroSchema.FLOAT64_OPTIONAL();
					else
						this.avroSchema = AvroSchema.FLOAT64_MANDATORY();
				}
				break;
			case "RAW":
				jdbcType = Types.BINARY;
				if (this.nullable)
					this.avroSchema = AvroSchema.BYTES_OPTIONAL();
				else
					this.avroSchema = AvroSchema.BYTES_MANDATORY();
				break;
			case "CHAR":
				jdbcType = Types.CHAR;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
			case "NCHAR":
				jdbcType = Types.NCHAR;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
			case "VARCHAR2":
				jdbcType = Types.VARCHAR;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
			case "NVARCHAR2":
				jdbcType = Types.NVARCHAR;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
			case "BLOB":
				jdbcType = Types.BLOB;
				if (this.nullable)
					this.avroSchema = AvroSchema.BYTES_OPTIONAL();
				else
					this.avroSchema = AvroSchema.BYTES_MANDATORY();
				break;
			case "CLOB":
				jdbcType = Types.CLOB;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
			case "TIMESTAMP":
			case "TIMESTAMP(0)":
			case "TIMESTAMP(1)":
			case "TIMESTAMP(3)":
			case "TIMESTAMP(6)":
			case "TIMESTAMP(9)":
				if (this.nullable)
					this.avroSchema = AvroSchema.TIMESTAMP_OPTIONAL();
				else
					this.avroSchema = AvroSchema.TIMESTAMP_MANDATORY();
				jdbcType = Types.TIMESTAMP;
				break;
			default:
				jdbcType = Types.VARCHAR;
				if (this.nullable)
					this.avroSchema = AvroSchema.STRING_OPTIONAL();
				else
					this.avroSchema = AvroSchema.STRING_MANDATORY();
				break;
		}
		this.avroSchema.setField(columnName);
	}

	/**
	 * 
	 * Constructor for pure Kafka connect schema...
	 * 
	 * @param resultSet
	 * @param keySchemaBuilder
	 * @param valueSchemaBuilder
	 * @throws SQLException
	 */
	public OraColumn(final ResultSet resultSet, final SchemaBuilder keySchema, final SchemaBuilder valueSchema) throws SQLException {
		this.columnName = resultSet.getString("COLUMN_NAME");
		final String partOfPkString = resultSet.getString("PK");
		if (!resultSet.wasNull() && "Y".equals(partOfPkString))
			this.partOfPk = true;
		else
			this.partOfPk = false;
		this.nullable = "Y".equals(resultSet.getString("NULLABLE")) ? true : false;
		final String oraType = resultSet.getString("DATA_TYPE");
		switch (oraType) {
			case "DATE":
				// Oracle Date holds time too...
				// So here we use Timestamp
				oracleDate = true;
				jdbcType = Types.TIMESTAMP;
				if (this.nullable)
					valueSchema.field(this.columnName, Timestamp.builder().optional().build());
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Timestamp.builder().required().build());
					else
						valueSchema.field(this.columnName, Timestamp.builder().required().build());
				break;
			case "FLOAT":
				final int floatDataPrecision = resultSet.getInt("DATA_PRECISION");
				if (resultSet.wasNull() || floatDataPrecision > 22) {
					jdbcType = Types.DOUBLE;
					if (this.nullable)
						valueSchema.field(this.columnName, Schema.OPTIONAL_FLOAT64_SCHEMA);
					else
						if (this.partOfPk)
							keySchema.field(this.columnName, Schema.FLOAT64_SCHEMA);
						else
							valueSchema.field(this.columnName, Schema.FLOAT64_SCHEMA);
				} else if (floatDataPrecision > 10) {
					jdbcType = Types.FLOAT;
					if (this.nullable)
						valueSchema.field(this.columnName, Schema.OPTIONAL_FLOAT32_SCHEMA);
					else
						if (this.partOfPk)
							keySchema.field(this.columnName, Schema.FLOAT32_SCHEMA);
						else
							valueSchema.field(this.columnName, Schema.FLOAT32_SCHEMA);
				} else {
					dataScale = (int) Math.ceil((float) floatDataPrecision * LOG_2);
					jdbcType = Types.DECIMAL;
					if (this.nullable)
						valueSchema.field(this.columnName, Decimal.builder(dataScale).optional().build());
					else
						if (this.partOfPk)
							keySchema.field(this.columnName, Decimal.builder(dataScale).required().build());
						else
							valueSchema.field(this.columnName, Decimal.builder(dataScale).required().build());
				}
				break;
			case "NUMBER":
				final int dataPrecision = resultSet.getInt("DATA_PRECISION");
				final boolean precisionIsNull = resultSet.wasNull();
				dataScale = resultSet.getInt("DATA_SCALE");
				final boolean scaleIsNull = resultSet.wasNull();
				if (precisionIsNull && scaleIsNull) {
					// NUMBER w/out precision and scale
					// OEBS and other legacy systems specific
					// Can be Integer or decimal or float....
					jdbcType = Types.DOUBLE;
					if (this.nullable)
						valueSchema.field(this.columnName, Schema.OPTIONAL_FLOAT64_SCHEMA);
					else
						if (this.partOfPk)
							keySchema.field(this.columnName, Schema.FLOAT64_SCHEMA);
						else
							valueSchema.field(this.columnName, Schema.FLOAT64_SCHEMA);
				} else if (dataScale == 0) {
					if (dataPrecision < 3) {
						jdbcType = Types.TINYINT;
						if (this.nullable)
							valueSchema.field(this.columnName, Schema.OPTIONAL_INT8_SCHEMA);
						else
							if (this.partOfPk)
								keySchema.field(this.columnName, Schema.INT8_SCHEMA);
							else
								valueSchema.field(this.columnName, Schema.INT8_SCHEMA);
					}
					else if (dataPrecision < 5) {
						jdbcType = Types.SMALLINT;
						if (this.nullable)
							valueSchema.field(this.columnName, Schema.OPTIONAL_INT16_SCHEMA);
						else
							if (this.partOfPk)
								keySchema.field(this.columnName, Schema.INT16_SCHEMA);
							else
								valueSchema.field(this.columnName, Schema.INT16_SCHEMA);
					}
					else if (dataPrecision < 10) {
						jdbcType = Types.INTEGER;
						if (this.nullable)
							valueSchema.field(this.columnName, Schema.OPTIONAL_INT32_SCHEMA);
						else
							if (this.partOfPk)
								keySchema.field(this.columnName, Schema.INT32_SCHEMA);
							else
								valueSchema.field(this.columnName, Schema.INT32_SCHEMA);
					}
					else if (dataPrecision < 19) {
						jdbcType = Types.BIGINT;
						if (this.nullable)
							valueSchema.field(this.columnName, Schema.OPTIONAL_INT64_SCHEMA);
						else
							if (this.partOfPk)
								keySchema.field(this.columnName, Schema.INT64_SCHEMA);
							else
								valueSchema.field(this.columnName, Schema.INT64_SCHEMA);
					} else {
						jdbcType = Types.DECIMAL;
						if (this.nullable)
							valueSchema.field(this.columnName, Decimal.builder(0).optional().build());
						else
							if (this.partOfPk)
								keySchema.field(this.columnName, Decimal.builder(0).required().build());
							else
								valueSchema.field(this.columnName, Decimal.builder(0).required().build());
					}
				} else {
					// Decimal values
					jdbcType = Types.DECIMAL;
					if (this.nullable)
						valueSchema.field(this.columnName, Decimal.builder(dataScale).optional().build());
					else
						if (this.partOfPk)
							keySchema.field(this.columnName, Decimal.builder(dataScale).required().build());
						else
							valueSchema.field(this.columnName, Decimal.builder(dataScale).required().build());
				}
				break;
			case "RAW":
				jdbcType = Types.BINARY;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_BYTES_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.BYTES_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.BYTES_SCHEMA);
				break;
			case "CHAR":
				jdbcType = Types.CHAR;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
			case "NCHAR":
				jdbcType = Types.NCHAR;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
			case "VARCHAR2":
				jdbcType = Types.VARCHAR;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
			case "NVARCHAR2":
				jdbcType = Types.NVARCHAR;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
			case "BLOB":
				jdbcType = Types.BLOB;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_BYTES_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.BYTES_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.BYTES_SCHEMA);
				break;
			case "CLOB":
				jdbcType = Types.CLOB;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
			case "TIMESTAMP":
			case "TIMESTAMP(0)":
			case "TIMESTAMP(1)":
			case "TIMESTAMP(3)":
			case "TIMESTAMP(6)":
			case "TIMESTAMP(9)":
				jdbcType = Types.TIMESTAMP;
				if (this.nullable)
					valueSchema.field(this.columnName, Timestamp.builder().optional().build());
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Timestamp.builder().required().build());
					else
						valueSchema.field(this.columnName, Timestamp.builder().required().build());
				break;
			default:
				jdbcType = Types.VARCHAR;
				if (this.nullable)
					valueSchema.field(this.columnName, Schema.OPTIONAL_STRING_SCHEMA);
				else
					if (this.partOfPk)
						keySchema.field(this.columnName, Schema.STRING_SCHEMA);
					else
						valueSchema.field(this.columnName, Schema.STRING_SCHEMA);
				break;
		}
	}

	/**
	 * Used in standalone sink connector
	 * 
	 * 
	 * @param avroSchema
	 * @param partOfPk
	 */
	public OraColumn(final AvroSchema avroSchema, final boolean partOfPk) {
		this.avroSchema = avroSchema;
		this.columnName = avroSchema.getField();
		this.nullable = avroSchema.isOptional();
		this.partOfPk = partOfPk;
		switch (avroSchema.getType()) {
		case AvroSchema.TYPE_INT8:
			jdbcType = Types.TINYINT;
			break;
		case AvroSchema.TYPE_INT16:
			jdbcType = Types.SMALLINT;
			break;
		case AvroSchema.TYPE_INT32:
			if (avroSchema.getName() != null && AvroSchema.TYPE_NAME_DATE.equals(avroSchema.getName()))
				jdbcType = Types.DATE;
			else
				jdbcType = Types.INTEGER;
			break;
		case AvroSchema.TYPE_INT64:
			if (avroSchema.getName() != null && AvroSchema.TYPE_NAME_TIMESTAMP.equals(avroSchema.getName()))
				jdbcType = Types.TIMESTAMP;
			else
				jdbcType = Types.BIGINT;
			break;
		case AvroSchema.TYPE_FLOAT32:
			jdbcType = Types.FLOAT;
			break;
		case AvroSchema.TYPE_FLOAT64:
			jdbcType = Types.DOUBLE;
			break;
		case AvroSchema.TYPE_STRING:
			jdbcType = Types.VARCHAR;
			break;
		case AvroSchema.TYPE_BOOLEAN:
			jdbcType = Types.BOOLEAN;
			break;
		case AvroSchema.TYPE_BYTES:
			jdbcType = Types.BINARY;
			break;
		default:
			jdbcType = Types.VARCHAR;
			break;
		}
	}

	/**
	 * Used internally for ROWID support
	 * 
	 * @param columnName
	 * @param partOfPk
	 * @param jdbcType
	 * @param nullable
	 */
	private OraColumn(
			final String columnName,
			final boolean partOfPk,
			final int jdbcType,
			boolean nullable) {
		this.columnName = columnName;
		this.partOfPk = partOfPk;
		this.jdbcType = jdbcType;
		this.nullable = nullable;
	}

	public static OraColumn getRowIdKey(final SchemaBuilder keySchema) {
		OraColumn rowIdColumn = new OraColumn(ROWID_KEY, true, Types.ROWID, false);
		if (keySchema == null)
			rowIdColumn.setAvroSchema(AvroSchema.STRING_MANDATORY());
		else
			keySchema.field(ROWID_KEY, Schema.STRING_SCHEMA);
		return rowIdColumn;
	}

	public String getColumnName() {
		return columnName;
	}

	public boolean isPartOfPk() {
		return partOfPk;
	}

	public int getJdbcType() {
		return jdbcType;
	}

	public boolean isNullable() {
		return nullable;
	}

	public AvroSchema getAvroSchema() {
		return avroSchema;
	}
	private void setAvroSchema(AvroSchema avroSchema) {
		this.avroSchema = avroSchema;
	}

	public boolean isOracleDate() {
		return oracleDate;
	}

	public int getDataScale() {
		return dataScale;
	}

	/**
	 * 
	 * @param statement
	 * @param columnNo
	 * @param data
	 * @throws SQLException
	 */
	public void bindWithPrepStmt(
			final PreparedStatement statement,
			final int columnNo,
			final Map<String, Object> data) throws SQLException  {
		final Object columnValue = data.get(columnName);
		switch (jdbcType) {
		case Types.DATE:
			//TODO Timezone support!!!!
			if (columnValue == null)
				statement.setNull(columnNo, Types.DATE);
			else
				statement.setDate(columnNo, new java.sql.Date((Long) data.get(columnName)));
			break;
		case Types.TIMESTAMP:
			//TODO Timezone support!!!!
			if (columnValue == null)
				statement.setNull(columnNo, Types.TIMESTAMP);
			else
				statement.setTimestamp(columnNo, new java.sql.Timestamp((Long) data.get(columnName)));
			break;
		case Types.BOOLEAN:
			if (columnValue == null)
				statement.setNull(columnNo, Types.BOOLEAN);
			else
				statement.setBoolean(columnNo, (boolean) data.get(columnName));
			break;
		case Types.TINYINT:
			if (columnValue == null)
				statement.setNull(columnNo, Types.TINYINT);
			else
				statement.setByte(columnNo, ((Integer) data.get(columnName)).byteValue());
			break;
		case Types.SMALLINT:
			if (columnValue == null)
				statement.setNull(columnNo, Types.SMALLINT);
			else
				statement.setShort(columnNo, ((Integer) data.get(columnName)).shortValue());
			break;
		case Types.INTEGER:
			if (columnValue == null)
				statement.setNull(columnNo, Types.INTEGER);
			else
				statement.setInt(columnNo, (Integer) data.get(columnName));
			break;
		case Types.BIGINT:
			if (columnValue == null)
				statement.setNull(columnNo, Types.BIGINT);
			else
				try {
					statement.setLong(columnNo, (Long) data.get(columnName));
				} catch (ClassCastException cce) {
					statement.setLong(columnNo, (Integer) data.get(columnName));
				}
			break;
		case Types.FLOAT:
			if (columnValue == null)
				statement.setNull(columnNo, Types.FLOAT);
			else
				statement.setFloat(columnNo, (float) data.get(columnName));
			break;
		case Types.DOUBLE:
			if (columnValue == null)
				statement.setNull(columnNo, Types.DOUBLE);
			else
				statement.setDouble(columnNo, (double) data.get(columnName));
			break;
		case Types.DECIMAL:
			if (columnValue == null)
				statement.setNull(columnNo, Types.DECIMAL);
			else
				statement.setBigDecimal(columnNo, (BigDecimal) data.get(columnName));
			break;
		case Types.BINARY:
			if (columnValue == null)
				statement.setNull(columnNo, Types.BINARY);
			else
				statement.setBytes(columnNo, (byte[]) data.get(columnName));
			break;
		case Types.VARCHAR:
			if (columnValue == null)
				statement.setNull(columnNo, Types.VARCHAR);
			else
				statement.setString(columnNo, (String) data.get(columnName));
			break;
		default:
			throw new SQLException("Unsupported data type!!!");
		}
	}

}