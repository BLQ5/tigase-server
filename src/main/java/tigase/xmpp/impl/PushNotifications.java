/**
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
package tigase.xmpp.impl;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.DataForm;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.annotation.DiscoFeatures;
import tigase.xmpp.impl.annotation.Handle;
import tigase.xmpp.impl.annotation.Handles;
import tigase.xmpp.impl.annotation.Id;
import tigase.xmpp.jid.BareJID;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.xmpp.impl.PushNotifications.XMLNS;

/**
 * Created by andrzej on 30.12.2016.
 */
@Bean(name = PushNotifications.ID, parent = SessionManager.class, active = true, exportable = true)
@Id(PushNotifications.ID)
@DiscoFeatures({PushNotifications.ID})
@Handles({@Handle(path = {Iq.ELEM_NAME, "enable"}, xmlns = XMLNS),
		  @Handle(path = {Iq.ELEM_NAME, "disable"}, xmlns = XMLNS),
		  @Handle(path = {Message.ELEM_NAME}, xmlns = Message.CLIENT_XMLNS)})
public class PushNotifications
		extends AbstractPushNotifications
		implements XMPPProcessorIfc, OfflineMessages.Notifier {

	private static final Logger log = Logger.getLogger(PushNotifications.class.getCanonicalName());
	
	@Inject(nullAllowed = true)
	protected MessageDeliveryProviderIfc message;
	
	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository nonAuthUserRepository,
						Queue<Packet> results, Map<String, Object> map) throws XMPPException {
		try {
			if (packet.getElemName() == Message.ELEM_NAME) {
				if (packet.getType() != StanzaType.groupchat) {
					return;
				}
				if (session == null || !this.message.hasConnectionForMessageDelivery(session)) {
					return;
				}
				if (packet.getStanzaTo() == null) {
					return;
				}
				if (session.isUserId(packet.getStanzaTo().getBareJID()) && packet.getStanzaTo().getResource() == null) {
					sendPushNotification(packet, results);
				}
				return;
			} else {
				super.process(packet, session, nonAuthUserRepository, results, map);
			}
		} catch (NotAuthorizedException ex) {
			results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet, "Session is not authorized", true));
		} catch (TigaseDBException ex) {
			results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, null, true));
		}
	}

	@Override
	public void notifyNewOfflineMessage(Packet packet, XMPPResourceConnection session, Queue<Packet> results,
										Map<String, Object> map) {
		if (packet.getElemName() != tigase.server.Message.ELEM_NAME) {
			return;
		}

		try {
			sendPushNotification(packet, results);
		} catch (UserNotFoundException ex) {
			log.log(Level.FINEST, "Could not send push notification for message " + packet, ex);
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "Could not send push notification for message " + packet, ex);
		}
	}

	@Override
	public void notifyOfflineMessagesRetrieved(XMPPResourceConnection session, Queue<Packet> results) {
		try {
			BareJID userJid = session.getBareJID();
			Map<String, Element> pushServices = getPushServices(userJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Offline messages retrieved push notifications for JID: {0}, pushServices: {1}",
				        new Object[]{userJid, pushServices});
			}
			if (pushServices.isEmpty()) {
				return;
			}

			notifyOfflineMessagesRetrieved(userJid, pushServices.values(), results::offer);
		} catch (UserNotFoundException | NotAuthorizedException ex) {
			log.log(Level.FINEST, "Could not send push notification about offline message retrieval by " + session, ex);
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "Could not send push notification about offline message retrieval by " + session,
					ex);
		}
	}
	
	protected void notifyOfflineMessagesRetrieved(BareJID userJid, Collection<Element> pushServices,
												  Consumer<Packet> results) {
		Element notification = new Element("notification", new String[]{"xmlns"}, new String[]{XMLNS});

		Element x = new Element("x", new String[]{"xmlns"}, new String[]{"jabber:x:data"});
		notification.addChild(x);
		DataForm.addFieldValue(notification, "message-count", String.valueOf(0l));

		sendPushNotification(userJid, pushServices, notification, results);
	}
	
}
