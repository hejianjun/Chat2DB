export interface IDownloadOptions {
  onProgress?: (percent: number) => void;
}

export function downloadFile(url: string, params: any, options?: IDownloadOptions) {
  const { onProgress } = options || {};

  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(params),
  })
    .then((response) => {
      const contentDisposition = response.headers.get('content-disposition');
      const filename = contentDisposition ? decodeURIComponent(contentDisposition.split("''")[1]) : 'file.txt';

      const contentLength = response.headers.get('content-length');
      const total = contentLength ? parseInt(contentLength, 10) : 0;

      if (!response.body) {
        return response.blob().then((blob) => ({ blob, filename }));
      }

      const reader = response.body.getReader();
      const chunks: Uint8Array[] = [];
      let received = 0;

      return new Promise<{ blob: Blob; filename: string }>((resolve) => {
        const pump = (): Promise<void | { blob: Blob; filename: string }> =>
          reader.read().then(({ done, value }) => {
            if (done) {
              const blob = new Blob(chunks);
              resolve({ blob, filename });
              return;
            }
            chunks.push(value);
            received += value.length;
            if (total > 0 && onProgress) {
              onProgress(Math.round((received / total) * 100));
            }
            return pump();
          });
        pump();
      });
    })
    .then(({ blob, filename }) => {
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.style.display = 'none';
      a.href = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);
      if (onProgress) {
        onProgress(100);
      }
    })
    .catch((error) => {
      console.error('下载文件失败:', error);
    });
}
