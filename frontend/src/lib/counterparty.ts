import type { Counterparty } from './api'

export type Key = Exclude<keyof Counterparty, 'id'>
export type EntityType = Counterparty['entityType']
export const KIND: Record<Counterparty['kind'], string> = { CUSTOMER: 'Müştəri', SUPPLIER: 'Təchizatçı', BOTH: 'Hər ikisi' }
export const ENTITY: Record<EntityType, string> = { LEGAL: 'Hüquqi şəxs', INDIVIDUAL: 'Fiziki şəxs' }

/** Hüquqi şəxs: ortaq və xüsusi sahələr (kind = rol) */
export const COMPANY: [Key, string][] = [['name', 'Hüquqi ad'], ['kind', 'Rol'], ['voen', 'VÖEN'], ['director', 'Direktor'], ['address', 'Hüquqi ünvan'], ['phone', 'Telefon'], ['email', 'E-poçt']]
export const BANK: [Key, string][] = [['iban', 'IBAN'], ['bank', 'Bank adı'], ['bankCode', 'Bank kodu (BİC)'], ['bankVoen', 'Bankın VÖEN-i'], ['swift', 'SWIFT'], ['correspondentAccount', 'Müxbir hesab']]

/** Fiziki şəxs: VÖEN, direktor, bank kodu/SWIFT yoxdur */
export const INDIVIDUAL: [Key, string][] = [['name', 'Ad Soyad Ata adı'], ['kind', 'Rol'], ['fin', 'FİN'], ['phone', 'Telefon'], ['email', 'E-poçt'], ['address', 'Ünvan']]
export const INDIVIDUAL_BANK: [Key, string][] = [['iban', 'IBAN'], ['cardNumber', 'Kart nömrəsi']]
