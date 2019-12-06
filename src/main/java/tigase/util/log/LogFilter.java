/*
 * Tigase XMPP Server - The instant messaging server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.util.log;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * @author kobit Created Dec 20, 2011
 */
public class LogFilter
		implements Filter {

	private String id = null;
	private String[] trackers = null;

	public LogFilter(String id, String... trackers) {
		this.id = id;
		this.trackers = trackers;
	}

	public String getId() {
		return id;
	}

	@Override
	public boolean isLoggable(LogRecord record) {
		boolean matchTracker = false;
		String msg = record.getMessage();
		if (msg != null) {
			int i = 0;
			while (!matchTracker && i < trackers.length) {
				matchTracker = msg.contains(trackers[i++]);
			}
		}
		return matchTracker;
	}

}
