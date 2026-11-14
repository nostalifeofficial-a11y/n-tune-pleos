import {sqliteTable,text,integer,index} from 'drizzle-orm/sqlite-core';
export const channels=sqliteTable('channels',{
 code:text('code').primaryKey(),writeHash:text('write_hash').notNull(),created:integer('created').notNull(),expires:integer('expires').notNull(),result:text('result'),updated:integer('updated'),activeRequest:text('active_request'),
 appSeenAt:integer('app_seen_at'),receivedId:text('received_id'),receivedAt:integer('received_at'),
},t=>[index('channels_expiry').on(t.expires)]);
export const limits=sqliteTable('request_limits',{key:text('key').primaryKey(),count:integer('count').notNull(),expires:integer('expires').notNull()},t=>[index('limits_expiry').on(t.expires)]);
