import { renderAsync } from 'docx-preview'
import { fetchBlob } from './api'

export const DOCX_OPTIONS = { className: 'docx', inWrapper: true, breakPages: true, ignoreLastRenderedPageBreak: false }

/** Yeni pəncərədə render edib çap dialoqunu açır (PDF kimi saxlamaq üçün) */
export async function printDocx(path: string) {
  const w = window.open('', '_blank')
  try {
    const blob = await fetchBlob(path)
    if (!w) throw new Error('Yeni pəncərə bloklandı, brauzerdə pop-up icazəsi verin')
    w.document.title = 'Çap'
    const style = w.document.createElement('style')
    style.textContent = 'body{margin:0;background:#fff}.docx-wrapper{background:#fff!important;padding:0!important}.docx-wrapper>section.docx{box-shadow:none!important;margin:0!important}@page{margin:0}'
    w.document.head.appendChild(style)
    const holder = w.document.createElement('div')
    w.document.body.appendChild(holder)
    await renderAsync(blob, holder, undefined, DOCX_OPTIONS)
    setTimeout(() => w.print(), 400)
  } catch (e) {
    w?.close()
    throw e
  }
}
