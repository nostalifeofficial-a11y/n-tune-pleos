CREATE TABLE `channels` (
	`code` text PRIMARY KEY NOT NULL,
	`write_hash` text NOT NULL,
	`created` integer NOT NULL,
	`expires` integer NOT NULL,
	`result` text,
	`updated` integer
);
--> statement-breakpoint
CREATE INDEX `channels_expiry` ON `channels` (`expires`);--> statement-breakpoint
CREATE TABLE `request_limits` (
	`key` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `limits_expiry` ON `request_limits` (`expires`);