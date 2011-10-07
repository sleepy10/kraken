/*
 * Copyright 2011 Future Systems
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.krakenapps.logdb;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.krakenapps.bnf.Syntax;

import org.krakenapps.logdb.query.FileBufferList;
import org.krakenapps.logdb.query.command.Lookup;
import org.krakenapps.logdb.query.command.Table;
import org.krakenapps.logdb.query.parser.DropParser;
import org.krakenapps.logdb.query.parser.SearchParser;
import org.krakenapps.logdb.query.parser.FieldsParser;
import org.krakenapps.logdb.query.parser.LookupParser;
import org.krakenapps.logdb.query.parser.OptionParser;
import org.krakenapps.logdb.query.parser.QueryParser;
import org.krakenapps.logdb.query.parser.RenameParser;
import org.krakenapps.logdb.query.parser.SortParser;
import org.krakenapps.logdb.query.parser.FunctionParser;
import org.krakenapps.logdb.query.parser.StatsParser;
import org.krakenapps.logdb.query.parser.TableParser;
import org.krakenapps.logdb.query.parser.TimechartParser;
import org.krakenapps.logstorage.LogStorage;
import org.krakenapps.logstorage.LogTableRegistry;
import org.krakenapps.logstorage.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public abstract class LogQueryCommand {
	public static enum Status {
		Waiting, Running, End
	}

	private static Logger logger = LoggerFactory.getLogger(LogQueryCommand.class);
	private static Syntax syntax = new Syntax();
	private String queryString;
	private int pushCount;
	protected LogQuery logQuery;
	protected String[] header;
	protected String dateColumnName;
	protected LogQueryCommand next;
	private boolean callbackTimeline;
	protected volatile Status status = Status.Waiting;

	static {
		List<Class<? extends QueryParser>> parsers = Arrays.asList(DropParser.class, SearchParser.class,
				FieldsParser.class, FunctionParser.class, LookupParser.class, OptionParser.class, RenameParser.class,
				SortParser.class, StatsParser.class, TableParser.class, TimechartParser.class);

		for (Class<? extends QueryParser> parser : parsers) {
			try {
				parser.newInstance().addSyntax(syntax);
			} catch (Exception e) {
				logger.error("kraken logstorage: failed to add syntax: " + parser.getSimpleName(), e);
			}
		}
	}

	public static LogQueryCommand createCommand(LogQueryService service, LogQuery logQuery, LogStorage logStorage,
			LogTableRegistry tableRegistry, String query) throws ParseException {
		LogQueryCommand token = (LogQueryCommand) syntax.eval(query);
		token.queryString = query;
		token.logQuery = logQuery;

		if (token instanceof Table) {
			((Table) token).setStorage(logStorage);
			TableMetadata tm = tableRegistry.getTableMetadata(tableRegistry.getTableId(((Table) token).getTableName()));
			if (tm.get("logformat") != null)
				((Table) token).setDataHeader(tm.get("logformat").split(" "));
		} else if (token instanceof Lookup) {
			((Lookup) token).setLogQueryService(service);
		}

		return token;
	}

	public String getQueryString() {
		return queryString;
	}

	protected void setDataHeader(String[] header) {
		this.header = header;
	}

	protected String getDateColumnName() {
		if (isReducer())
			return null;
		return dateColumnName;
	}

	protected void setDateColumnName(String dateColumnName) {
		this.dateColumnName = dateColumnName;
	}

	protected Object getData(String key, Map<String, Object> m) {
		if (m.containsKey(key))
			return m.get(key);

		if (header != null) {
			for (int i = 0; i < header.length; i++) {
				if (header[i].equals(key)) {
					String data = (String) m.get("_data");
					int l = 0;
					for (int j = 0; j < i; j++) {
						l = data.indexOf(' ', l) + 1;
						if (l == 0)
							return null;
					}
					int r = data.indexOf(' ', l);

					String value = null;
					if (r == -1)
						value = data.substring(l);
					else
						value = data.substring(l, r);

					m.put(key, value);
					return value;
				}
			}
		}

		return null;
	}

	public LogQueryCommand getNextCommand() {
		return next;
	}

	public void setNextCommand(LogQueryCommand next) {
		this.next = next;
		setNextCommandConfigure();
	}

	private void setNextCommandConfigure() {
		if (next != null) {
			next.setDataHeader(header);
			next.setDateColumnName(getDateColumnName());
			next.setNextCommandConfigure();
		}
	}

	public Status getStatus() {
		return status;
	}

	public void init() {
		this.status = Status.Waiting;
	}

	public void start() {
		throw new UnsupportedOperationException();
	}

	public int getPushCount() {
		return pushCount;
	}

	public abstract void push(Map<String, Object> m);

	public void push(FileBufferList<Map<String, Object>> buf) {
		if (buf != null) {
			for (Map<String, Object> m : buf)
				push(m);
			buf.close();
		}
	}

	protected final void write(Map<String, Object> m) {
		pushCount++;
		if (next != null && next.status != Status.End) {
			if (callbackTimeline) {
				for (LogTimelineCallback callback : logQuery.getTimelineCallbacks())
					callback.put((Date) m.get(dateColumnName));
			}
			next.status = Status.Running;
			next.push(m);
		}
	}

	protected final void write(FileBufferList<Map<String, Object>> buf) {
		pushCount += buf.size();
		if (next != null && next.status != Status.End) {
			next.status = Status.Running;
			next.push(buf);
		} else {
			buf.close();
		}
	}

	public abstract boolean isReducer();

	public boolean isCallbackTimeline() {
		return callbackTimeline;
	}

	public void setCallbackTimeline(boolean callbackTimeline) {
		this.callbackTimeline = callbackTimeline;
	}

	public void eof() {
		status = Status.End;

		if (next != null && next.status != Status.End)
			next.eof();

		if (logQuery != null) {
			if (callbackTimeline) {
				for (LogTimelineCallback callback : logQuery.getTimelineCallbacks())
					callback.callback();
				logQuery.getTimelineCallbacks().clear();
			}
			if (logQuery.getCommands().get(0).status != Status.End)
				logQuery.getCommands().get(0).eof();
		}
	}
}