package rasPager;

import java.util.ArrayList;
import java.util.TimerTask;

public class Scheduler extends TimerTask {
	//
	protected int time = 0x0000;
	protected int delay = 0;

	// if active is true, time is increased each run
	protected boolean active = true;

	// max time value
	protected final int max = (int) (Math.pow(2, 16));
	// protected final int [] maxBatch = {0, 7, 15, 23, 32, 40, 48, 56, 65, 65,
	// 65, 65, 65, 65, 65, 65, 65};

	// send time (countdown)
	protected double sendTime = 0.0;

	// delay time (for serial) (countdown)
	protected int serialDelay = 0;

	// data list (codewords)
	protected ArrayList<Integer> data;

	protected Log log = null;

	// write message into log file (log level normal)
	protected void log(String message, int type) {
		log(message, type, Log.DEBUG_SENDING);
	}

	// write message with given log level into log file
	protected void log(String message, int type, int logLevel) {
		if (this.log != null) {
			this.log.println(message, type, logLevel);
		}
	}

	// constructor
	public Scheduler(Log log) {
		this.log = log;
	}

	// "main"
	public void run() {
		// if active
		if (active) {
			// increase time
			// this.time = ++this.time % this.max;
			this.time = ((int) (System.currentTimeMillis() / 100) + this.delay) % this.max;

			// if serial delay is lower than or equals 0
			if (this.serialDelay <= 0) {
				// decrease send time
				this.sendTime -= 0.1;
			}

			// decrease serial delay
			this.serialDelay -= 100;
		}

		// check slot
		char slot = Main.timeSlots.getCurrentSlot(time);
		boolean isLastSlot = Main.timeSlots.isLastSlot(slot);

		// get count of active slots
		int slotCount = Main.timeSlots.checkSlot(slot);
		// log("Scheduler: checkSlot# Erlaubter Slot (" + slot + ") - Anzahl " +
		// slotCount, );

		// if slot is not the same as the last slot
		if (!isLastSlot) {
			// draw all slots
			Main.drawSlots();
		}

		// if send time is lower than or equals 0
		if (this.sendTime <= 0) {
			// set pin to off
			Main.radioComm.setOff();
		}

		// if send time is lower than or equals 0 and there is at least 1 slot
		// and current slot is not the same as last slot
		// and the message queue is not empty
		if (this.sendTime <= 0 && slotCount > 0 && !isLastSlot && !Main.messageQueue.isEmpty()) {
			log("Scheduler: checkSlot# Erlaubter Slot (" + slot + ") - Anzahl " + slotCount, Log.INFO);

			// set serial delay
			this.serialDelay = 0;

			// get data
			getData(slotCount);

			// set pin to on
			Main.radioComm.setOn();
		}

		// if there is data
		if (this.serialDelay <= 0 && data != null) {
			Main.radioComm.sendByte(data);
			data = null;
		}
	}

	// get data depending on slot count
	public void getData(int slotCount) {
		// send batches
		// max batches per slot: (slot time - praeambel time) / bps / ((frames +
		// (1 = sync)) * bits per frame)
		// (3,75 - 0,48) * 1200 / ((16 + 1) * 32)
		// int maxBatch = (int)((3.75 * slotCount - 0.48 / 1000) * 1200 / 544);
		int maxBatch = (int) ((6.4 * slotCount - 0.48) * 1200 / 544);

		// create data
		data = new ArrayList<Integer>();

		// add praeembel
		for (int i = 0; i < 18; i++) {
			data.add(Pocsag.PRAEEMBEL);
		}

		// get messages as long as message queue is not empty
		while (!Main.messageQueue.isEmpty()) {
			// get message
			Message message = Main.messageQueue.pop();

			// get codewords and frame position
			ArrayList<Integer> cwBuf = message.getCodeWords();
			int framePos = cwBuf.get(0);
			int cwCount = cwBuf.size() - 1;

			// Falls message nicht mehr passt (da dann zu viele Batches),
			// zurück in Queue

			// (data.size() - 18) / 17 = aktBatches
			// aktBatches + (cwCount + 2 * framePos) / 16 + 1 = Batches NACH
			// hinzufügen
			// also Batches NACH hinzufügen > maxBatches, dann keine neue
			// Nachricht holen
			// if((cwCount + 2 * framePos + data.size()) / 16 > maxBatch) {
			// if count of batches + this message is greater than max batches
			if (((data.size() - 18) / 17 + (cwCount + 2 * framePos) / 16 + 1) > maxBatch) {
				// push message back in queue (first position)
				Main.messageQueue.addFirst(message);
				break;
			}

			// each batch starts with sync-word
			data.add(Pocsag.SYNC);

			// add idle-words until frame position is reached
			for (int c = 0; c < framePos; c++) {
				data.add(Pocsag.IDLE);
				data.add(Pocsag.IDLE);
			}

			// add data
			for (int c = 1; c < cwBuf.size(); c++) {
				if ((data.size() - 18) % 17 == 0)
					data.add(Pocsag.SYNC);

				data.add(cwBuf.get(c));
			}

			// fill batch with idle-words
			while ((data.size() - 18) % 17 != 0) {
				data.add(Pocsag.IDLE);
			}
		}

		// infos about max batches
		log("Scheduler: # used batches (" + ((data.size() - 18) / 17) + " / " + maxBatch + ")", Log.INFO);

		// set send time
		// data.size() * 32 = bits / 1200 = send time
		this.sendTime = data.size() * 2.0 / 75.0 + 0.1;
	}

	// get current time
	public int getTime() {
		return time;
	}

	// correct time by delay
	/*
	 * public void correctTime(int delay) { // if delay equals 0, there is no
	 * correction needed if(delay == 0) return;
	 * 
	 * // set active to false this.active = false;
	 * 
	 * // if delay is greater 0 if(delay > 0) { // then add delay to current
	 * time this.time = (this.time + delay) % this.max; } // if delay is lower 0
	 * else if(delay < 0) { // then add delay to current time (after that check
	 * if time is lower than 0) if((this.time += delay) < 0) { this.time +=
	 * this.max - 1; } }
	 * 
	 * // set active to true this.active = true; }
	 */

	// correct time by delay
	public void correctTime(int delay) {
		this.delay += delay;

	}
}